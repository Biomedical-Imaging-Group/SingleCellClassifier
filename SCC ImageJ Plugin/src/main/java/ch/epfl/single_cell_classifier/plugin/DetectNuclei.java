package ch.epfl.single_cell_classifier.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.config.NCModel;
import ch.epfl.single_cell_classifier.utils.IndependentPercentileNormalizer;
import ch.epfl.single_cell_classifier.utils.LUTHelper;
import de.csbdresden.stardist.StarDist2DAccessor;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ops.OpService;

@Plugin(type = Command.class, label = "Detect Nuclei", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Single Cell Classifier"),
		@Menu(label = "Detection"),
		@Menu(label = "Detect Nuclei", weight = 1)
}) 
public class DetectNuclei implements Command{

	@Parameter
	protected CommandService command;

	@Parameter
	protected UIService ui;

	@Parameter
	protected OpService opService;

	@Parameter
	protected DatasetService datasetService;

	private static final String CONFIG_CHOICE_FILE = "Config (.json) from File";

	@Parameter(label="Config", choices = {
		NCConfig.CONFIG_HUMAN_MOUSE_HE_PDX,
		CONFIG_CHOICE_FILE
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String configChoice;

	@Parameter
	private Dataset input;

	public static final String OUTPUT_BOTH = "Both";
	public static final String OUTPUT_ROI = "ROI Manager";
	public static final String OUTPUT_LABEL_IMAGE = "Label Image";

	@Parameter(label="Output Type", choices={OUTPUT_ROI, OUTPUT_LABEL_IMAGE, OUTPUT_BOTH}, style=ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	private String outputType = OUTPUT_BOTH;

	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>Advanced Options</b></html>")
	private final String advMsg = "<html><br/><hr width='100'></html>";

	@Parameter(label=CONFIG_CHOICE_FILE, required = false)
	private File configFile;

	private static final String MODEL_CHOICE_CONFIG = "Model of config";
	private static final String MODEL_CHOICE_FILE = "Model (.zip) from File";
	
	@Parameter(label="Segmentation Model", choices = {
		MODEL_CHOICE_CONFIG,
		NCModel.MODEL_SEGMENTATION_HUMAN_MOUSE_HE_PDX,
		StarDist2DAccessor.MODEL_DSB2018_HEAVY_AUGMENTATION,
		StarDist2DAccessor.MODEL_HE_HEAVY_AUGMENTATION,
		StarDist2DAccessor.MODEL_DSB2018_PAPER,
		MODEL_CHOICE_FILE
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String segmentationModelChoice = MODEL_CHOICE_CONFIG;

	@Parameter(label="Segmentation Model (.zip) from File", required = false)
	private File segmentationModelFile;

	@Parameter(label="Max Tile Size", min="1", stepSize="1")
	private int maxTileSize = 512;

	@Parameter(type=ItemIO.OUTPUT)
	private Dataset nucleiLabel;

	@Override
	public void run() {
		try {
			NCConfig config = getConfig();
			File segmentationModelFile = getModelFile(config);

			//Normalize input
			Dataset normalizedInput = getNormalizedInput(config);

			int horizontalTiles = (int)Math.ceil(normalizedInput.getWidth() / (double)maxTileSize);
			int verticalTiles = (int)Math.ceil(normalizedInput.getHeight() / (double)maxTileSize);

			final HashMap<String, Object> paramsStarDist = new HashMap<>();
			paramsStarDist.put("input", normalizedInput);
			
			if(segmentationModelFile == null) {
				paramsStarDist.put("modelChoice", segmentationModelChoice);
			}else {
				paramsStarDist.put("modelChoice", "Model (.zip) from File");				
			}
			
			paramsStarDist.put("normalizeInput", config.getSegmentationNormalize() && !config.getSegmentationIndependentChannelsNormalization());
			paramsStarDist.put("percentileBottom", config.getSegmentationNormalizationPercentileLow());
			paramsStarDist.put("percentileTop", config.getSegmentationNormalizationPercentileHigh());
			paramsStarDist.put("probThresh", config.getSegmentationProbThreshold());
			paramsStarDist.put("nmsThresh", config.getSegmentationNMSThreshold());
			paramsStarDist.put("outputType", outputType);
			paramsStarDist.put("modelFile", segmentationModelFile);
			paramsStarDist.put("nTiles", horizontalTiles * verticalTiles);

			final Future<CommandModule> futureStarDist = command.run(de.csbdresden.stardist.StarDist2D.class, false, paramsStarDist);
			nucleiLabel = (Dataset) futureStarDist.get().getOutput("label");
			
			nucleiLabel.initializeColorTables(1);
			nucleiLabel.setColorTable(LUTHelper.getGlasbeyColorTable(), 0);
			nucleiLabel.setName(input.getName() + " nuclei label");

		}catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}

	private File getModelFile(NCConfig config) {
		if(segmentationModelChoice.equals(MODEL_CHOICE_CONFIG))
			segmentationModelChoice = config.getSegmentationModelName();
		switch(segmentationModelChoice) {
		case MODEL_CHOICE_FILE:
			return segmentationModelFile;
		default:
			NCModel model = NCModel.SEGMENTATION_MODELS.getOrDefault(segmentationModelChoice, null);
			if(model == null) return null;
			return model.getModelFile();
		}
	}

	private NCConfig getConfig() {
		switch(configChoice) {
		case CONFIG_CHOICE_FILE:
			return new NCConfig(configFile);
		default:
			return NCConfig.CONFIGS.get(configChoice);
		}
	}

	/**
	 * Normalize the input independently if required
	 * @return the normalized input
	 */
	private Dataset getNormalizedInput(NCConfig segmentationConfig) {
		if(segmentationConfig.getSegmentationNormalize() && segmentationConfig.getSegmentationIndependentChannelsNormalization()) {
			IndependentPercentileNormalizer normalizer = new IndependentPercentileNormalizer();

			float[] percentiles = new float[]{
				(float)segmentationConfig.getSegmentationNormalizationPercentileLow(),
				(float)segmentationConfig.getSegmentationNormalizationPercentileHigh()
			};

			normalizer.setup(percentiles, new float[] {0f, 1f}, false); //Setup normalizer
			final Dataset normalizedInput = normalizer.normalize(input, opService, datasetService);

			return normalizedInput;
		}
		return input;
	}

}
