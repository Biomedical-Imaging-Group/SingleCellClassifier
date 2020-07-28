package ch.epfl.single_cell_classifier.plugin;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import com.google.gson.Gson;

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.config.NCModel;
import de.csbdresden.stardist.StarDist2DAccessor;
import net.imagej.ops.OpService;

@Plugin(type = Command.class, label = "Config Creator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Single Cell Classifier"),
		@Menu(label = "Utilities"),
		@Menu(label = "Config Creator", weight = 1)
}) 
public class ConfigCreator implements Command{
	@Parameter
	protected OpService opService;

	@Parameter
	protected CommandService command;

	private static final String CONFIG_CHOICE_FILE = "Config (.json) from File";

	@Parameter(label="Config", choices = {
			NCConfig.CONFIG_HUMAN_MOUSE_HE_PDX,
			CONFIG_CHOICE_FILE
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String configChoice;

	@Parameter(label=CONFIG_CHOICE_FILE, required = false)
	private File configFile;

	@Parameter(label="Load", callback="loadConfig")
	private Button loadButton;

	@Parameter(label="Output Config File (.json)", style = "save")
	private File outputFile;

	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>Segmentation</b></html>")
	private final String segmentationMsg = "<html><br/><hr width='100'></html>";

	private static final String MODEL_CHOICE_NOT_DEFINED = "Not defined";

	@Parameter(label="Segmentation Model", choices = {
			MODEL_CHOICE_NOT_DEFINED,
			NCModel.MODEL_SEGMENTATION_HUMAN_MOUSE_HE_PDX,
			StarDist2DAccessor.MODEL_DSB2018_HEAVY_AUGMENTATION,
			StarDist2DAccessor.MODEL_HE_HEAVY_AUGMENTATION,
			StarDist2DAccessor.MODEL_DSB2018_PAPER
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String segmentationModelChoice;

	@Parameter(label="Normalize")
	private boolean normalize = false;

	@Parameter(label="Independent Channels Normalization")
	private boolean independentChannelsNormalization = false;

	@Parameter(label="Percentile Low", stepSize="0.1", min="0", max="100", style=NumberWidget.SLIDER_STYLE, callback="percentileLowChanged")
	private double percentileLow = 0;

	@Parameter(label="Percentile High", stepSize="0.1", min="0", max="100", style=NumberWidget.SLIDER_STYLE, callback="percentileHighChanged")
	private double percentileHigh = 100;

	@Parameter(label="Probability/Score Threshold", stepSize="0.05", min="0", max="1", style=NumberWidget.SLIDER_STYLE)
	private double probThresh = 0.5;

	@Parameter(label="Overlap Threshold", stepSize="0.05", min="0", max="1", style=NumberWidget.SLIDER_STYLE)
	private double nmsThresh = 0.5;

	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>Measurements</b></html>")
	private final String measurementsMsg = "<html><br/><hr width='100'></html>";

	@Parameter(label="Cell Thickness", stepSize="0.05", min="0")
	private double cellThickness = 0;

	private static final String UNIT_UM = "um";
	private static final String UNIT_PIXELS = "pixel";

	@Parameter(label="Cell Thickness Unit", choices = {
			UNIT_UM,
			UNIT_PIXELS
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String cellThicknessUnit;

	@Parameter(label="Nuclei Channels Factor")
	private String nucleiChannelsFactor = "[]";

	@Parameter(label="Cytoplasms Channels Factor")
	private String cytoplasmsChannelsFactor = "[]";

	@Parameter(label="K Neighbours")
	private String kNeighbours = "[]";

	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>Classification</b></html>")
	private final String classificationMsg = "<html><br/><hr width='100'></html>";

	@Parameter(label="Classification Model", choices = {
			MODEL_CHOICE_NOT_DEFINED,
			NCModel.MODEL_CLASSIFICATION_HUMAN_MOUSE_HE_PDX
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String classificationModelChoice;

	@Parameter(label="Classes")
	private String classes = "[]";

	@Parameter(label="Classes color")
	private String classesColor = "[]";
	
	@Override
	public void run() {
		HashMap<String, Object> map = new HashMap<>();
		HashMap<String, Object> segmentationMap = new HashMap<>();
		HashMap<String, Object> measureMap = new HashMap<>();
		HashMap<String, Object> classificationMap = new HashMap<>();
		
		if(segmentationModelChoice != MODEL_CHOICE_NOT_DEFINED)
			segmentationMap.put("model_name", segmentationModelChoice);
		segmentationMap.put("normalize", normalize);
		segmentationMap.put("independent_channels_normalization", independentChannelsNormalization);
		segmentationMap.put("prob_threshold", probThresh);
		segmentationMap.put("NMS_threshold", nmsThresh);
		segmentationMap.put("normalization_percentile_low", percentileLow);
		segmentationMap.put("normalization_percentile_high", percentileHigh);

		measureMap.put("cell_thickness", cellThickness);
		measureMap.put("cell_thickness_unit", cellThicknessUnit);
		measureMap.put("nuclei_channels_factor", stringToDoubleArray(nucleiChannelsFactor));
		measureMap.put("cytoplasms_channels_factor", stringToDoubleArray(cytoplasmsChannelsFactor));
		measureMap.put("k_neighbours", stringToIntArray(kNeighbours));

		if(classificationModelChoice != MODEL_CHOICE_NOT_DEFINED)
			classificationMap.put("model_name", classificationModelChoice);
		classificationMap.put("classes", stringToStringArray(classes));
		classificationMap.put("classes_color", stringToStringArray(classesColor));

		map.put("segmentation", segmentationMap);
		map.put("measure", measureMap);
		map.put("classification", classificationMap);
		
		try {
		    // create a writer
		    FileWriter writer = new FileWriter(outputFile);

		    // convert map to JSON File
		    new Gson().toJson(map, writer);

		    // close the writer
		    writer.close();
	    } catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void loadConfig() {
		NCConfig config;
		switch(configChoice) {
		case CONFIG_CHOICE_FILE:
			config = new NCConfig(configFile);
		default:
			config = NCConfig.CONFIGS.get(configChoice);
		}
		
		segmentationModelChoice = MODEL_CHOICE_NOT_DEFINED;
		if(config.getSegmentationModelName() != null)
			segmentationModelChoice = config.getSegmentationModelName();
		
		normalize = config.getSegmentationNormalize();
		independentChannelsNormalization = config.getSegmentationIndependentChannelsNormalization();
		percentileLow = config.getSegmentationNormalizationPercentileLow();
		percentileHigh = config.getSegmentationNormalizationPercentileHigh();
		probThresh = config.getSegmentationProbThreshold();
		nmsThresh = config.getSegmentationNMSThreshold();
		
		cellThickness = config.getCellThickness();
		cellThicknessUnit = config.isCellThicknessInPixel() ? UNIT_PIXELS : UNIT_UM;
		nucleiChannelsFactor = doubleArrayToString(config.getNucleiChannelsFactor());
		cytoplasmsChannelsFactor = doubleArrayToString(config.getCytoplasmsChannelsFactor());
		kNeighbours = intArrayToString(config.getKNeighbours());
		
		classificationModelChoice = MODEL_CHOICE_NOT_DEFINED;
		if(config.getClassificationModelName() != null)
			classificationModelChoice = config.getClassificationModelName();
		classes = "[" + String.join(", ", config.getClasses()) + "]";
		classesColor = colorArrayToString(config.getClassesColor());
	}

	private void percentileLowChanged() {
		percentileHigh = Math.max(percentileLow, percentileHigh);
	}

	private void percentileHighChanged() {
		percentileLow = Math.min(percentileLow, percentileHigh);
	}

	private String doubleArrayToString(double[] array) {
		String [] stringArray = new String[array.length];
		
		for(int i = 0; i < array.length; ++i) {
			stringArray[i] = String.valueOf(array[i]);
		}
		
		return "[" + String.join(", ", stringArray) + "]";
	}
	
	private String intArrayToString(int[] array) {
		String [] stringArray = new String[array.length];
		
		for(int i = 0; i < array.length; ++i) {
			stringArray[i] = String.valueOf(array[i]);
		}
		
		return "[" + String.join(", ", stringArray) + "]";
	}
	
	private String colorArrayToString(Color[] array) {
		String [] stringArray = new String[array.length];
		
		for(int i = 0; i < array.length; ++i) {
			String colorString = ("000000" + Integer.toHexString(array[i].getRGB() & 0xFFFFFF));
			stringArray[i] = "#"+colorString.substring(colorString.length() - 6);
		}
		
		return "[" + String.join(", ", stringArray) + "]";
	}
	
	private double[] stringToDoubleArray(String s) {
		s = s.replace("[", "");
		s = s.replace("]", "");
		s = s.replace(" ", "");
		
		String[] stringArray = s.split(",");
		double[] array = new double[stringArray.length];
		
		for(int i = 0; i < array.length; ++i) {
			array[i] = Double.parseDouble(stringArray[i]);
		}
		
		return array;
	}
	
	private int[] stringToIntArray(String s) {
		s = s.replace("[", "");
		s = s.replace("]", "");
		s = s.replace(" ", "");

		String[] stringArray = s.split(",");
		int[] array = new int[stringArray.length];
		
		for(int i = 0; i < array.length; ++i) {
			array[i] = Integer.parseUnsignedInt(stringArray[i]);
		}
		
		return array;
	}
	
	private String[] stringToStringArray(String s) {
		s = s.replace("[", "");
		s = s.replace("]", "");
		s = s.replace(" ", "");

		String[] stringArray = s.split(",");
		
		return stringArray;
	}
}
