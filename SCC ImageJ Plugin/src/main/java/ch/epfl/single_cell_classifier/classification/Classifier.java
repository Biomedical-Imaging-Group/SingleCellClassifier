package ch.epfl.single_cell_classifier.classification;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.config.NCModel;
import ch.epfl.single_cell_classifier.measure.CellInformation;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.real.FloatType;

public class Classifier {

	public static void classify(File classificationModelFile, List<CellInformation> cells, CommandService command, DatasetService datasetService) {
		try {
			NCModel model = new NCModel(classificationModelFile, true);
			Dataset featuresDataset = getFeaturesDataset(model, cells, datasetService);
			
			final HashMap<String, Object> paramsCNN = new HashMap<>();
			paramsCNN.put("input", featuresDataset);
			paramsCNN.put("normalizeInput", false);
			paramsCNN.put("clip", false);
			paramsCNN.put("nTiles", 1);
			paramsCNN.put("blockMultiple", 64);
			paramsCNN.put("batchSize", 1);
			paramsCNN.put("modelFile", classificationModelFile);
			paramsCNN.put("showProgressDialog", false);
			final Future<CommandModule> futureCNN = command.run(de.csbdresden.csbdeep.commands.GenericNetwork.class, false, paramsCNN);
			final Dataset prediction = (Dataset) futureCNN.get().getOutput("output");

			//Store probabilities in cells
			final RandomAccess<FloatType> rai = (RandomAccess<FloatType>)prediction.getImgPlus().randomAccess();

			long[] pos = new long[2];
			for(int i = 0; i < cells.size(); ++i) {
				pos[0] = i;
				
				double[] probabilities = new double[(int) prediction.dimension(1)];
				for(int classId = 0; classId < prediction.dimension(1); ++classId) {
					pos[1] = classId;
					rai.setPosition(pos);
					
					probabilities[classId] = rai.get().get();
				}

				cells.get(i).setClassProbabilities(probabilities);
			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}

	private static Dataset getFeaturesDataset(NCModel model, List<CellInformation> cells, DatasetService datasetService) {
		int num_features = cells.get(0).getClassificationFeatures().length;
		int target_size = (int)(Math.ceil(num_features/4.0) * 4);
		long[] dims = new long[4];
		dims[0] = cells.size();
		dims[1] = 2;
		dims[2] = 2;
		dims[3] = target_size / 4;
		AxisType[] axes = new AxisType[4];
		axes[0] = Axes.TIME;
		axes[1] = Axes.Y;
		axes[2] = Axes.X;
		axes[3] = Axes.CHANNEL;

		Dataset featuresDataset = datasetService.create(new FloatType(), dims, "input", axes);
		final RandomAccess<FloatType> rai = (RandomAccess<FloatType>)featuresDataset.getImgPlus().randomAccess();

		final long[] pos = new long[4];
		for(int i = 0; i < cells.size(); ++i) {
			pos[0] = i;
			double[] features = cells.get(i).getClassificationFeatures();
			for(int k = 0; k < 2; ++k) {
				pos[1] = k;
				for(int l = 0; l < 2; ++l) {
					pos[2] = l;
					for(int m = 0; m < target_size / 4; ++m) {
						pos[3] = m;
						rai.setPosition(pos);
						int featPos = k * (target_size / 2) + l * (target_size / 4) + m;

						float value = 0f;
						if(featPos < features.length)
							value = normalizeFeature(model, features[featPos], featPos);

						rai.get().set(value);
					}
				}
			}
		}

		return featuresDataset;
	}

	private static float normalizeFeature(NCModel model, double feature, int pos) {
		return (float) ((feature - model.getClassificationMeanValues()[pos]) / model.getClassificationStdevValues()[pos]);
	}

	public static double[][] getClassProbabilities(NCConfig config, List<CellInformation> cells) {
		double[][] probabilities = new double[config.getClasses().length][cells.size()];

		for(int i = 0; i < cells.size(); ++i) {
			double[] classProbabilities = cells.get(i).getClassProbabilities();
			for(int j = 0; j < classProbabilities.length; ++j)
				probabilities[j][i] = classProbabilities[j];
		}

		return probabilities;
	}
}
