package ch.epfl.single_cell_classifier.utils;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.NumericType;

public class ChannelsToGrayConverter{

	public static ImagePlus convert(ImagePlus imp, double[] channelFactor, DatasetService datasetService, OpService opService) {
		
		if(imp.getBitDepth() != 8) {
			//Normalize before converting to 8 bit
			Dataset inputDataset = datasetService.create((RandomAccessibleInterval)ImagePlusAdapter.wrapImgPlus(imp));
			
			IndependentPercentileNormalizer normalizer = new IndependentPercentileNormalizer();
			final Dataset normalizedDataset = normalizer.normalize(inputDataset, opService, datasetService);
	
			imp = ImageJFunctions.wrap((RandomAccessibleInterval<? extends NumericType>) normalizedDataset.getImgPlus(), "normalized");
		}
		
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
			
			if(ip.getBitDepth() != 8) {
				ip = ip.convertToByteProcessor();
			}
			
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
