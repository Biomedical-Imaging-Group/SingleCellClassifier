package ch.epfl.single_cell_classifier.plugin;

import java.io.File;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.utils.ChannelsToGrayConverter;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.NumericType;

@Plugin(type = Command.class, label = "Get Gray Level", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Single Cell Classifier"),
		@Menu(label = "Utilities"),
		@Menu(label = "Get Gray Level", weight = 3)
}) 
public class GetGrayLevel implements Command{
	@Parameter(label="Source")
	private Dataset source;

	private static final String CONFIG_CHOICE_FILE = "Config (.json) from File";

	@Parameter(label="Config", choices = {
		NCConfig.CONFIG_HUMAN_MOUSE_HE_PDX,
		CONFIG_CHOICE_FILE
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String configChoice;

	private final static String WEIGHT_TYPE_NUCLEI = "Nuclei";
	private final static String WEIGHT_TYPE_CYTOPLASMS = "Cytoplasms";
	
    @Parameter(label="Weight Type", choices={WEIGHT_TYPE_NUCLEI, WEIGHT_TYPE_CYTOPLASMS}, style=ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
    private String weightType = WEIGHT_TYPE_NUCLEI;

	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>Advanced Options</b></html>")
	private final String advMsg = "<html><br/><hr width='100'></html>";

	@Parameter(label=CONFIG_CHOICE_FILE, required = false)
	private File configFile;
    
	@Parameter(label="Gray Source", type = ItemIO.OUTPUT)
	private ImagePlus graySource;

	@Override
	public void run() {		
		NCConfig config = getConfig();

		ImagePlus sourceIp = ImageJFunctions.wrap((RandomAccessibleInterval<? extends NumericType>) source.getImgPlus(), "source");
		
		if(weightType.equals(WEIGHT_TYPE_NUCLEI))
			graySource = ChannelsToGrayConverter.convert(sourceIp, config.getNucleiChannelsFactor());
		else if(weightType.equals(WEIGHT_TYPE_CYTOPLASMS))
			graySource = ChannelsToGrayConverter.convert(sourceIp, config.getCytoplasmsChannelsFactor());
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
