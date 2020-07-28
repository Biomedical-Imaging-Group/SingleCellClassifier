package ch.epfl.single_cell_classifier.measure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import ij.process.ImageProcessor;

public class TextureAnalyzer {
	private final static int NB_VALUES = 256;
	private final static int LOG_RESOLUTION = 1000;

	public final static int FEATURE_ID_ANGULAR_SECOND_MOMENT = 0;
	public final static int FEATURE_ID_CONTRAST = 1;
	public final static int FEATURE_ID_CORRELATION = 2;
	public final static int FEATURE_ID_SUM_OF_SQUARES_VARIANCE = 3;
	public final static int FEATURE_ID_INVERSE_DIFFERENCE_MOMENT = 4;
	public final static int FEATURE_ID_SUM_AVERAGE = 5;
	public final static int FEATURE_ID_SUM_VARIANCE = 6;
	public final static int FEATURE_ID_SUM_ENTROPY = 7;
	public final static int FEATURE_ID_ENTROPY = 8;
	public final static int FEATURE_ID_DIFFERENCE_VARIANCE = 9;
	public final static int FEATURE_ID_DIFFERENCE_ENTROPY = 10;
	public final static int FEATURE_ID_INFORMATION_MEASURES_CORRELATION_1 = 11;
	public final static int FEATURE_ID_INFORMATION_MEASURES_CORRELATION_2 = 12;

	public final static String[] FEATURES_NAME = new String[] {
			"Angular second moment",
			"Contrast",
			"Correlation",
			"Sum of squares: variance",
			"Inverse difference moment",
			"Sum average",
			"Sum variance",
			"Sum entropy",
			"Entropy",
			"Difference variance",
			"Difference entropy",
			"Information measures correlation 1",
			"Information measures correlation 2"
	};

	private static double[] logValues =  new double[LOG_RESOLUTION];;
	private static boolean[] logValuesComputed = new boolean[LOG_RESOLUTION];;

	public static double[][] computeTextureFeatures(ImageProcessor ip, ImageProcessor labelImage, int maxLabel) {
		int[] dxs = new int[] {1, 1, 0, -1};
		int[] dys = new int[] {0, 1, 1, 1};

		double[][] meanHaralick = new double[maxLabel][13];

		for(int i = 0; i < dxs.length; ++i) {
			List<HashMap<IntensityPair, Double>> glcm = computeGLCM(ip, labelImage, maxLabel, dxs[i], dys[i]);
			for(int label = 1; label <= maxLabel; ++label) {
				double[] curHaralick = computeHaralickTextureFeatures(glcm.get(label - 1));
				for(int j = 0; j < curHaralick.length; ++j) {
					meanHaralick[label - 1][j] += curHaralick[j] / dxs.length;
				}
			}
		}

		return meanHaralick;
	}

	private static double[] computeHaralickTextureFeatures(HashMap<IntensityPair, Double> glcm) {
		double[] pSum = new double[NB_VALUES * 2 - 1];
		double[] pDif = new double[NB_VALUES];
		double[] pI = new double[NB_VALUES];
		double meanI = 0;
		double pIEntropy = 0;
		double angularSecondMoment = 0;
		double entropy = 0;
		for(Entry<IntensityPair, Double> entry : glcm.entrySet()) {
			pSum[entry.getKey().getI() + entry.getKey().getJ()] += entry.getValue();
			pDif[Math.abs(entry.getKey().getI() - entry.getKey().getJ())] += entry.getValue();
			pI[entry.getKey().getI()] += entry.getValue();

			angularSecondMoment += entry.getValue() * entry.getValue();
			entropy -= entry.getValue() * log(entry.getValue());
		}

		for(int i = 0; i < NB_VALUES; ++i) {
			if(pI[i] == 0) continue;

			meanI += i * pI[i];
			pIEntropy -= pI[i] * log(pI[i]);	
		}
		
		double correlation = 0;
		double HXY1 = 0;
		double HXY2 = 0;
		double sumOfSquaresVariance = 0;
		for(int i = 0; i < NB_VALUES; ++i) {
			if(pI[i] == 0) continue; //If pI is 0 then all glcm[i][*] are 0
			for(int j = 0; j < NB_VALUES; ++j) {
				Double proba = glcm.get(new IntensityPair(i, j));
				if(proba != null) {
					correlation += (i - meanI) * (j - meanI) * proba;

					HXY1 -= proba * log(pI[i] * pI[j]);
				}
				if(pI[j] == 0) continue;
				
				HXY2 -= pI[i] * pI[j] * log(pI[i] * pI[j]);
			}
			sumOfSquaresVariance += (i - meanI) * (i - meanI) * pI[i];
		}
		if(sumOfSquaresVariance > 0)
			correlation /= sumOfSquaresVariance;

		double sumAverage = 0;
		double sumVariance = 0;
		double sumEntropy = 0;
		for(int k = 0; k < pSum.length; ++k){
			if(pSum[k] == 0) continue;

			sumAverage += k * pSum[k];
			if(pSum[k] >= Double.MIN_VALUE)
				sumEntropy -= pSum[k] * log(pSum[k]);
		}

		for(int k = 0; k < pSum.length; ++k){
			if(pSum[k] == 0) continue;

			sumVariance += (k - sumEntropy) * (k - sumEntropy) * pSum[k];
		}

		double meanDif = 0;
		for(int k = 0; k < pDif.length; ++k){
			if(pDif[k] == 0) continue;

			meanDif += k * pDif[k];
		}

		double contrast = 0;
		double inverseDifferenceMoment = 0;
		double differenceVariance = 0;
		double differenceEntropy = 0;
		for(int k = 0; k < pDif.length; ++k){
			if(pDif[k] == 0) continue;

			contrast += k * k * pDif[k];
			inverseDifferenceMoment += pDif[k] / (1 + k * k);
			differenceEntropy -= pDif[k] * log(pDif[k]);
			differenceVariance += (k - meanDif) * (k - meanDif) * pDif[k];
		}

		double informationMeasuresCorrelation1 = 0;
		if(pIEntropy > 0)
			informationMeasuresCorrelation1 = (entropy - HXY1) / pIEntropy;
		double tempCor2 = Math.exp(-2 * (HXY2 - entropy));
		double informationMeasuresCorrelation2 = 0;
		if(tempCor2 <= 1)
			informationMeasuresCorrelation2 = Math.sqrt(1 - tempCor2);

		return new double[] {
				angularSecondMoment,
				contrast,
				correlation,
				sumOfSquaresVariance,
				inverseDifferenceMoment,
				sumAverage,
				sumVariance,
				sumEntropy,
				entropy,
				differenceVariance,
				differenceEntropy,
				informationMeasuresCorrelation1,
				informationMeasuresCorrelation2
		};
	}

	private static double log(double n) {
		int index = getLogIndex(n);
		if(!logValuesComputed[index])
			computeLogValues(index);
		return logValues[index];
	}

	private static int getLogIndex(double n) {
		int index = (int) Math.round(n * LOG_RESOLUTION) - 1;
		if(index < 0)
			index = 0;
		if(index >= LOG_RESOLUTION)
			index = LOG_RESOLUTION - 1;
		return index;
	}

	private static void computeLogValues(int index) {
		logValues[index] = Math.log((index + 1) / (double)LOG_RESOLUTION);

		logValuesComputed[index] = true;
	}

	/**
	 * Compute Gray Level Co-occurrence Matrix
	 * @param ip the source image
	 * @param labelImage the label image
	 * @param maxLabel the maximum label
	 * @param dx displacement in the x axis (must be positive)
	 * @param dy displacement in the y axis (must be positive)
	 * @return
	 */
	private static List<HashMap<IntensityPair, Double>> computeGLCM(ImageProcessor ip, ImageProcessor labelImage, int maxLabel, int dx, int dy){
		List<HashMap<IntensityPair, Integer>> glcm = new ArrayList<HashMap<IntensityPair, Integer>>(maxLabel);
		for(int label = 1; label <= maxLabel; ++label) {
			glcm.add(new HashMap<IntensityPair, Integer>());
		}

		int nbPixels[] = new int[maxLabel];

		for(int y = 0; y < ip.getHeight(); ++y) {
			if(y + dy < 0 || y + dy >= ip.getHeight()) continue;
			for(int x = 0; x < ip.getWidth(); ++x) {
				if(x + dx < 0 || x + dx >= ip.getWidth()) continue;
				int label = (int) labelImage.getf(x, y);
				if(label == 0) continue;

				int label2 = (int) labelImage.getf(x + dx, y + dy);
				if(label2 != label) continue;

				int value1 = (int) ip.getf(x, y);
				int value2 = (int) ip.getf(x + dx, y + dy);

				HashMap<IntensityPair, Integer> curMap = glcm.get(label - 1);

				curMap.put(new IntensityPair(value1, value2), curMap.getOrDefault(new IntensityPair(value1, value2), 0) + 1);
				curMap.put(new IntensityPair(value2, value1), curMap.getOrDefault(new IntensityPair(value2, value1), 0) + 1);
				nbPixels[label - 1] += 2;
			}
		}

		List<HashMap<IntensityPair, Double>> glcm_result = new ArrayList<HashMap<IntensityPair, Double>>(maxLabel);

		for(int label = 1; label <= maxLabel; ++label) {
			HashMap<IntensityPair, Double> curMap = new HashMap<IntensityPair, Double>();
			glcm_result.add(curMap);
			if(nbPixels[label - 1] == 0) continue;
			for(Entry<IntensityPair, Integer> entry : glcm.get(label - 1).entrySet()) {
				curMap.put(entry.getKey(), entry.getValue() / (double)nbPixels[label - 1]);
			}
		}

		return glcm_result;
	}

	private static class IntensityPair{
		private int i;
		private int j;

		public IntensityPair(int i, int j) {
			this.i = i;
			this.j = j;
		}

		public int getI() {
			return i;
		}

		public int getJ() {
			return j;
		}

		@Override
		public int hashCode() {
			return i * NB_VALUES + j;
		}

		@Override
		public boolean equals(Object obj) {
			IntensityPair other = (IntensityPair)obj;
			return other.i == i && other.j == j;
		}
	}
}
