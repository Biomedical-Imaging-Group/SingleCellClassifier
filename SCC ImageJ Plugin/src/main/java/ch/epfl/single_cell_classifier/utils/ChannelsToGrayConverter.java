package ch.epfl.single_cell_classifier.utils;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

public class ChannelsToGrayConverter{

	public static ImagePlus convert(ImagePlus imp, double[] channelFactor) {
		if(imp.getNChannels() == 1) return (ImagePlus)imp.clone();

		ByteProcessor outP = new ByteProcessor(imp.getWidth(), imp.getHeight());
		ImagePlus out = new ImagePlus("gray " + imp.getTitle(), outP);

		double totalWeight = 0;
		for(int c = 0; c < channelFactor.length; ++c) {
			totalWeight += channelFactor[c];
		}
		if(totalWeight == 0) totalWeight = 1;
		for(int c = 0; c < channelFactor.length; ++c) {
			channelFactor[c] /= totalWeight;
		}

		for(int c = 0; c < imp.getNChannels(); ++c) {
			imp.setC(c + 1);
			ImageProcessor ip = imp.getProcessor();
			for(int x = 0; x < imp.getWidth(); ++x) {
				for(int y = 0; y < imp.getHeight(); ++y) {
					double value = ip.getPixel(x, y) * channelFactor[c];
					outP.putPixelValue(x, y, outP.getPixelValue(x, y) + value);
				}
			}
		}

		return out;
	}
}
