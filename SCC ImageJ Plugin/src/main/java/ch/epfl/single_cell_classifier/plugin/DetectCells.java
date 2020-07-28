package ch.epfl.single_cell_classifier.plugin;

import java.io.File;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.utils.LUTHelper;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.filter.EDM;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Reconstruction;
import inra.ijpb.morphology.strel.DiskStrel;
import inra.ijpb.watershed.Watershed;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ops.OpService;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

@Plugin(type = Command.class, label = "Detect Cells", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Single Cell Classifier"),
		@Menu(label = "Detection"),
		@Menu(label = "Detect Cells", weight = 3)
}) 
public class DetectCells implements Command{

	@Parameter
	protected OpService opService;

	@Parameter
	protected UIService uiService;

	@Parameter
	protected DatasetService datasetService;

	private static final String CONFIG_CHOICE_FILE = "Config (.json) from File";

	@Parameter(label="Config", choices = {
		NCConfig.CONFIG_HUMAN_MOUSE_HE_PDX,
		CONFIG_CHOICE_FILE
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String configChoice;

	@Parameter(label="Source")
	private Dataset source;

	@Parameter(label="Nuclei Label")
	private Dataset nucleiLabel;

	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>Advanced Options</b></html>")
	private final String advMsg = "<html><br/><hr width='100'></html>";

	@Parameter(label=CONFIG_CHOICE_FILE, required = false)
	private File configFile;

	@Parameter(label="Cells label", type=ItemIO.OUTPUT)
	private Dataset cellsLabel;

	@Override
	public void run() {
		NCConfig config = getConfig();
		double cellThickness = getCellThickness(config);

		//Compute nuclei mask
		IterableInterval<BitType> nucleiMask = opService.threshold().apply((IterableInterval<UnsignedShortType>)nucleiLabel.getImgPlus(), new UnsignedShortType(0));

		// Compute nuclei gradient
		ImagePlus nucleiLabelIp = ImageJFunctions.wrap((RandomAccessibleInterval<UnsignedShortType>) nucleiLabel.getImgPlus(), "nuclei label");
		ImagePlus nucleiGradIp = new ImagePlus("nuclei gradient", Morphology.externalGradient(nucleiLabelIp.getProcessor(), DiskStrel.fromRadius(1)));

		// Get border from gradient
		IterableInterval<BitType> nucleiBorder = opService.threshold().apply((IterableInterval)ImagePlusAdapter.wrapImgPlus(nucleiGradIp), new UnsignedShortType(0));

		// Remove border from nuclei mask
		IterableInterval<BitType> nucleiWithoutBorder = opService.math().subtract(nucleiMask, opService.math().multiply(nucleiMask, nucleiBorder));
		ImagePlus nucleiWithoutBorderIp = ImageJFunctions.wrap((RandomAccessibleInterval<BitType>) nucleiWithoutBorder, "nuclei without border");

		// Find voronoi of nuclei mask
		EDM edm = new EDM();
		edm.setup("voronoi", nucleiWithoutBorderIp);
		ImagePlus voronoiIp = new ImagePlus("voronoi", nucleiWithoutBorderIp.getProcessor());
		edm.run(voronoiIp.getProcessor());

		IterableInterval<BitType> voronoi = opService.threshold().apply((IterableInterval)ImagePlusAdapter.wrapImgPlus(voronoiIp), new UnsignedByteType(0));

		IterableInterval<BitType> invertedVoronoi = opService.copy().iterableInterval((IterableInterval<BitType>) voronoi);
		opService.image().invert(invertedVoronoi, voronoi);

		//Make sure that voronoi border include nuclei border
		IterableInterval<BitType> finalVoronoiMask = opService.math().subtract(invertedVoronoi, opService.math().multiply(invertedVoronoi, opService.math().multiply(nucleiMask, nucleiBorder)));

		//Compute cell mask
		IterableInterval<BitType> invertedNucleiMask = opService.copy().iterableInterval(nucleiMask);
		opService.image().invert(invertedNucleiMask, nucleiMask);
		IterableInterval<FloatType> nucleiDistance = (IterableInterval<FloatType>)opService.image().distancetransform((RandomAccessibleInterval<BitType>)invertedNucleiMask);

		IterableInterval<FloatType> invertedNucleiDistance = opService.copy().iterableInterval(nucleiDistance);
		opService.image().invert(invertedNucleiDistance, nucleiDistance);
		IterableInterval<BitType> cellMask = opService.threshold().apply(invertedNucleiDistance, new FloatType((float)-cellThickness));

		//Merge masks
		IterableInterval<BitType> finalMask = opService.math().multiply(cellMask, finalVoronoiMask);
		ImagePlus finalMaskIp = ImageJFunctions.wrap((RandomAccessibleInterval<BitType>) finalMask, "final mask");

		IterableInterval<UnsignedShortType> maskedNucleiLabel = opService.math().multiply((IterableInterval<UnsignedShortType>)nucleiLabel.getImgPlus(), (IterableInterval<UnsignedShortType>)opService.convert().uint16(finalMask));
		nucleiLabelIp = ImageJFunctions.wrap((RandomAccessibleInterval<UnsignedShortType>) maskedNucleiLabel, "nuclei label");

		//Make nuclei distance negative
		nucleiDistance = opService.math().subtract(nucleiDistance, new FloatType((float)cellThickness));

		//Restrict nuclei distance to mask
		nucleiDistance = opService.math().multiply(nucleiDistance, (IterableInterval<FloatType>)opService.convert().float32(finalMask));

		//Need to erode label or overflow over the mask
		ImagePlus reducedLabelIp = new ImagePlus("Reduced labels", Morphology.erosion(nucleiLabelIp.getProcessor(), DiskStrel.fromRadius(1)));

		ImagePlus cellsLabelIp = Watershed.computeWatershed(finalMaskIp, reducedLabelIp, finalMaskIp, 4, true, false);

		cellsLabelIp.setProcessor(Reconstruction.fillHoles(cellsLabelIp.getProcessor()));

		cellsLabel = datasetService.create((RandomAccessibleInterval)ImagePlusAdapter.wrapImgPlus(cellsLabelIp));

		cellsLabel.initializeColorTables(1);
		cellsLabel.setColorTable(LUTHelper.getGlasbeyColorTable(), 0);
		cellsLabel.setName(source.getName() + " cells label");
	}

	/**
	 * Get the cell thickness in pixel
	 */
	private double getCellThickness(NCConfig config) {
		if(config.isCellThicknessInPixel())
			return config.getCellThickness();

		ImagePlus sourceIp = ImageJFunctions.wrap((RandomAccessibleInterval<? extends NumericType>) source.getImgPlus(), "source");
		
		Calibration calib = sourceIp.getCalibration();

		double pixelPerUm = 1./calib.pixelWidth;

		return pixelPerUm * config.getCellThickness();
	}

	private NCConfig getConfig() {
		switch(configChoice) {
		case CONFIG_CHOICE_FILE:
			return new NCConfig(configFile);
		default:
			return NCConfig.CONFIGS.get(configChoice);
		}
	}
}
