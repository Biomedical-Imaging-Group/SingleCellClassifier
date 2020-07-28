package ch.epfl.single_cell_classifier.measure;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import inra.ijpb.geometry.Box2D;

public class ChannelsAnalyzer {
	public static double[][] computeChannelsFeatures(ImagePlus imp, ImageProcessor labelImage, int maxLabel) {
		double[][] channelsFeatures = new double[maxLabel][2 * imp.getNChannels()];//Succession of mean and variance

		for(int c = 0; c < imp.getNChannels(); ++c) {
			imp.setC(c + 1);

			ImageProcessor ip = imp.getProcessor();
			int[] nbPixels = new int[maxLabel];
			for(int y = 0; y < imp.getHeight(); ++y) {
				for(int x = 0; x < imp.getWidth(); ++x) {
					int label = (int) labelImage.getf(x, y);
					if(label == 0) continue;

					int value = ip.getPixel(x, y);
					channelsFeatures[label - 1][2 * c] += value;
					nbPixels[label - 1]++;
				}
			}

			for(int label = 1; label <= maxLabel; ++label) {
				if(nbPixels[label - 1] == 0) continue;
				channelsFeatures[label - 1][2 * c] /= nbPixels[label - 1];
			}

			for(int y = 0; y < imp.getHeight(); ++y) {
				for(int x = 0; x < imp.getWidth(); ++x) {
					int label = (int) labelImage.getf(x, y);
					if(label == 0) continue;

					int value = ip.getPixel(x, y);
					channelsFeatures[label - 1][2 * c + 1] += Math.pow(value - channelsFeatures[label - 1][2 * c], 2);
				}
			}

			for(int label = 1; label <= maxLabel; ++label) {
				channelsFeatures[label - 1][2 * c + 1] /= nbPixels[label - 1];
			}
		}

		return channelsFeatures;
	}

	public static double[] computeChannelsFeatures(ImagePlus imp, ImageProcessor labelImage, int label, Box2D boundingBox) {
		double[] channelsFeatures = new double[2 * imp.getNChannels()];//Succession of mean and stdev

		/*
		for(int c = 0; c < imp.getNChannels(); ++c) {
			imp.setC(c + 1);

			ImageProcessor ip = imp.getProcessor();
			int nbPixels = 0;
			for(int x = (int)boundingBox.getXMin(); x <= boundingBox.getXMax(); ++x) {
				for(int y = (int)boundingBox.getYMin(); y <= boundingBox.getYMax(); ++y) {
					if(labelImage.getPixel(x, y) != label) continue;

					int value = ip.getPixel(x, y);
					channelsFeatures[2 * c] += value;
					nbPixels++;
				}
			}
			if(nbPixels == 0) continue;

			channelsFeatures[2 * c] /= nbPixels;

			for(int x = (int)boundingBox.getXMin(); x <= boundingBox.getXMax(); ++x) {
				for(int y = (int)boundingBox.getYMin(); y <= boundingBox.getYMax(); ++y) {
					if(labelImage.getPixel(x, y) != label) continue;

					int value = imp.getProcessor().getPixel(x, y);
					channelsFeatures[2 * c + 1] += Math.pow(value - channelsFeatures[2 * c], 2);
				}
			}
			channelsFeatures[2 * c + 1] /= nbPixels;
			channelsFeatures[2 * c + 1] = Math.sqrt(channelsFeatures[2 * c + 1]);
		}
		 */

		return channelsFeatures;
	}
}
