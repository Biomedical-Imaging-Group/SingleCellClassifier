package ch.epfl.single_cell_classifier.utils;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.AxisType;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class FloatToSigmoidConverter <T extends RealType<T>>{
	/**
	 * Apply a sigmoid to a float image
	 * @param im
	 * @param datasetService
	 * @param center
	 * @param width the width of the curve (98% of the size)
	 * @param complement if we should take the complement of the sigmoid (1-sigmoid)
	 * @return
	 */
	public Dataset convert(Dataset im, DatasetService datasetService, double center, double width, boolean complement) {
		long[] dims = new long[im.numDimensions()];
		im.dimensions(dims);
		AxisType[] axes = new AxisType[im.numDimensions()];
		for (int i = 0; i < axes.length; i++) {
			axes[i] = im.axis(i).type();
		}

		final Dataset output = datasetService.create(new FloatType(), dims, "sigmoid", axes);
		final RandomAccess<T> in = (RandomAccess<T>)im.getImgPlus().randomAccess();
		final Cursor<FloatType> out = (Cursor<FloatType>) output.getImgPlus().localizingCursor();

		while (out.hasNext()) {
			out.fwd();
			in.setPosition(out);
			
			float result = (float)(1.0/(1.0 + Math.exp((-in.get().getRealDouble() + center) / (width / 10.0))));
			
			if(complement)
				result = 1 - result;
			
			out.get().setReal(result);
		}

		return output;
	}
	
	public Dataset convert(Dataset im, DatasetService datasetService, double center, double width) {
		return convert(im, datasetService, center, width, false);
	}
}
