package ch.epfl.single_cell_classifier.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.scijava.util.FileUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class NCModel {

	public static final String MODEL_SEGMENTATION_HUMAN_MOUSE_HE_PDX = "Nuclei Breast 20X H&E PDX";
	public static final String MODEL_CLASSIFICATION_HUMAN_MOUSE_HE_PDX = "Human/Mouse Breast 20X H&E PDX";

	public static final Map<String, NCModel> SEGMENTATION_MODELS = new HashMap<String, NCModel>();
	public static final Map<String, NCModel> CLASSIFICATION_MODELS = new HashMap<String, NCModel>();

	static {
		SEGMENTATION_MODELS.put(MODEL_SEGMENTATION_HUMAN_MOUSE_HE_PDX, new NCModel("models/segmentation/nuclei_breast_20X_HE_PDX.zip", false));

		CLASSIFICATION_MODELS.put(MODEL_CLASSIFICATION_HUMAN_MOUSE_HE_PDX, new NCModel("models/classification/human_mouse_breast_20X_HE_PDX.zip", true));
	}

	private String modelPath;
	private boolean isClassification;
	private File modelFile = null;
	private double[] classificationMeanValues = null;
	private double[] classificationStdevValues = null;

	public NCModel(String modelPath, boolean isClassification) {
		this.modelPath = modelPath;
		this.isClassification = isClassification;
	}
	
	public NCModel(File modelFile, boolean isClassification) {
		this.modelFile = modelFile;
		this.isClassification = isClassification;
	}

	public File getModelFile() {
		if(modelFile == null)
			modelFile = getFile(NCModel.class.getClassLoader().getResource(modelPath));
		return modelFile;
	}

	public double[] getClassificationMeanValues() {
		if(isClassification && classificationMeanValues == null)
			initClassificationNormalization();
		return classificationMeanValues;
	}

	public double[] getClassificationStdevValues() {
		if(isClassification && classificationStdevValues == null)
			initClassificationNormalization();
		return classificationStdevValues;
	}

	private void initClassificationNormalization() {
		try {
			ZipInputStream zip = new ZipInputStream(new FileInputStream(getModelFile()));
			ZipEntry entry;
			while((entry = zip.getNextEntry())!=null) {
				if(entry.getName().equals("normalization.json")) {
					StringBuilder s = new StringBuilder();
					byte[] buffer = new byte[1024];
					int read = 0;
					while ((read = zip.read(buffer, 0, 1024)) >= 0) {
						s.append(new String(buffer, 0, read));
					}
					
					JsonObject root = new JsonParser().parse(s.toString()).getAsJsonObject();
					JsonArray meanValuesJson = root.get("mean").getAsJsonArray();
					JsonArray stdevValuesJson = root.get("stdev").getAsJsonArray();

					classificationMeanValues = new double[meanValuesJson.size()];
					classificationStdevValues = new double[stdevValuesJson.size()];
					
					for(int i = 0; i < meanValuesJson.size(); ++i) {
						classificationMeanValues[i] = meanValuesJson.get(i).getAsDouble();
						classificationStdevValues[i] = stdevValuesJson.get(i).getAsDouble();
					}
					break;
				}
			}
			zip.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private File getFile(URL url) {
		try {
			String protocol = url.getProtocol().toLowerCase();
			switch (protocol) {
			case "file":
				return FileUtils.urlToFile(url);
			case "jar":
				File tmpModelFile = File.createTempFile(isClassification ? "classification_model_" : "segmentation_model_", ".zip");
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
