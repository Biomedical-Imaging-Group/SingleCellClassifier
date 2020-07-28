package ch.epfl.single_cell_classifier.config;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.scijava.util.FileUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class NCConfig {

	public static final String CONFIG_HUMAN_MOUSE_HE_PDX = "Human/Mouse Breast 20X H&E PDX";
	
	public static final Map<String, NCConfig> CONFIGS = new HashMap<String, NCConfig>();
	
	static {
		CONFIGS.put(CONFIG_HUMAN_MOUSE_HE_PDX, new NCConfig(getFile(NCConfig.class.getClassLoader().getResource("configs/human_mouse_breast_20X_HE_PDX.json"))));
	}
	
	private String segmentationModelName = null;
	private double segmentationProbThreshold;
	private double segmentationNMSThreshold;
	private boolean segmentationNormalize;
	private boolean segmentationIndependentChannelsNormalization;
	private double segmentationNormalizationPercentileLow;
	private double segmentationNormalizationPercentileHigh;
	private double cellThickness;
	private boolean isCellThicknessInPixel;
	private double[] nucleiChannelsFactor;
	private double[] cytoplasmsChannelsFactor;
	private int[] kNeighbours;
	private String classificationModelName = null;
	private String[] classes;
	private Color[] classesColor;

	public NCConfig(File configFile) {
		try {			
			JsonObject root = new JsonParser().parse(new FileReader(configFile)).getAsJsonObject();

			JsonObject segmentationInfo = root.get("segmentation").getAsJsonObject();
			JsonObject measureInfo = root.get("measure").getAsJsonObject();
			JsonObject classificationInfo = root.get("classification").getAsJsonObject();

			if(segmentationInfo.has("model_name"))
				segmentationModelName = segmentationInfo.get("model_name").getAsString();
			
			segmentationNormalize = segmentationInfo.get("normalize").getAsBoolean();
			segmentationIndependentChannelsNormalization = segmentationInfo.get("independent_channels_normalization").getAsBoolean();
			segmentationProbThreshold = segmentationInfo.get("prob_threshold").getAsDouble();
			segmentationNMSThreshold = segmentationInfo.get("NMS_threshold").getAsDouble();
			segmentationNormalizationPercentileLow = segmentationInfo.get("normalization_percentile_low").getAsDouble();
			segmentationNormalizationPercentileHigh = segmentationInfo.get("normalization_percentile_high").getAsDouble();

			cellThickness = measureInfo.get("cell_thickness").getAsDouble();
			isCellThicknessInPixel = measureInfo.get("cell_thickness_unit").getAsString() == "pixel";
			JsonArray nucleiChannelsFactorJson = measureInfo.get("nuclei_channels_factor").getAsJsonArray();
			JsonArray cytoplasmChannelsFactorJson = measureInfo.get("cytoplasms_channels_factor").getAsJsonArray();
			
			nucleiChannelsFactor = new double[nucleiChannelsFactorJson.size()];
			cytoplasmsChannelsFactor = new double[cytoplasmChannelsFactorJson.size()];
			
			for(int c = 0; c < nucleiChannelsFactorJson.size(); ++c) {
				nucleiChannelsFactor[c] = nucleiChannelsFactorJson.get(c).getAsDouble();
				cytoplasmsChannelsFactor[c] = cytoplasmChannelsFactorJson.get(c).getAsDouble();
			}
			
			JsonArray kNeighboursJson = measureInfo.get("k_neighbours").getAsJsonArray();
			
			kNeighbours = new int[kNeighboursJson.size()];
			
			for(int i = 0; i < kNeighboursJson.size(); ++i) {
				kNeighbours[i] = kNeighboursJson.get(i).getAsInt();
			}
			
			if(classificationInfo.has("model_name"))
				classificationModelName = classificationInfo.get("model_name").getAsString();

			JsonArray classesJson = classificationInfo.get("classes").getAsJsonArray();
			JsonArray classesColorJson = classificationInfo.get("classes_color").getAsJsonArray();
			
			classes = new String[classesJson.size()];
			classesColor = new Color[classesJson.size()];
			
			for(int i = 0; i < classesJson.size(); ++i) {
				classes[i] = classesJson.get(i).getAsString();
				classesColor[i] = Color.decode(classesColorJson.get(i).getAsString());
			}

		} catch (JsonSyntaxException | JsonIOException | FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public String getSegmentationModelName() {
		return segmentationModelName;
	}
	
	public boolean getSegmentationNormalize() {
		return segmentationNormalize;
	}

	public boolean getSegmentationIndependentChannelsNormalization() {
		return segmentationIndependentChannelsNormalization;
	}

	public double getSegmentationNormalizationPercentileLow() {
		return segmentationNormalizationPercentileLow;
	}

	public double getSegmentationNormalizationPercentileHigh() {
		return segmentationNormalizationPercentileHigh;
	}

	public double getSegmentationProbThreshold() {
		return segmentationProbThreshold;
	}

	public double getSegmentationNMSThreshold() {
		return segmentationNMSThreshold;
	}

	public double getCellThickness() {
		return cellThickness;
	}

	public boolean isCellThicknessInPixel() {
		return isCellThicknessInPixel;
	}
	
	public double[] getNucleiChannelsFactor() {
		return nucleiChannelsFactor;
	}
	
	public double[] getCytoplasmsChannelsFactor() {
		return cytoplasmsChannelsFactor;
	}
	
	public int[] getKNeighbours() {
		return kNeighbours;
	}

	public String getClassificationModelName() {
		return classificationModelName;
	}
	
	public String[] getClasses() {
		return classes;
	}
	
	public Color[] getClassesColor() {
		return classesColor;
	}
	
	private static File getFile(URL url) {
		try {
			String protocol = url.getProtocol().toLowerCase();
			switch (protocol) {
			case "file":
				return FileUtils.urlToFile(url);
			case "jar":
				File tmpModelFile;
				tmpModelFile = File.createTempFile("config_", ".json");
				Files.copy(url.openStream(), tmpModelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				return tmpModelFile;            
			default:
				return null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
