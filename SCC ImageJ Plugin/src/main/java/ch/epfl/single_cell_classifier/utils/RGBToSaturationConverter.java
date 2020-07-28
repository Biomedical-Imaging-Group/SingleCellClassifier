package ch.epfl.single_cell_classifier.utils;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.AxisType;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class RGBToSaturationConverter <T extends RealType<T>>{
	private static int NB_CHANNELS = 3;
	private static int NB_DIMS = 3;
	private static int CHANNEL_AXIS_ID = 2;

	public Dataset convert(Dataset im, DatasetService datasetService) {
		long[] inDims = new long[NB_DIMS];
		im.dimensions(inDims);
		long[] outDims = new long[NB_DIMS - 1];
		AxisType[] axes = new AxisType[NB_DIMS - 1];
		for (int i = 0; i < axes.length; i++) {
			axes[i] = im.axis(i).type();
			outDims[i] = inDims[i];
		}

		final Dataset output = datasetService.create(new FloatType(), outDims, "saturation", axes);
		final RandomAccess<T> in = (RandomAccess<T>)im.getImgPlus().randomAccess();
		final Cursor<FloatType> out = (Cursor<FloatType>) output.getImgPlus().localizingCursor();


		final long[] inPos = new long[NB_DIMS];
		final long[] outPos = new long[NB_DIMS - 1];
		while (out.hasNext()) {
			out.fwd();
			out.localize(outPos);
			
			inPos[0] = outPos[0];
			inPos[1] = outPos[1];
			
			float maxValue = Float.MIN_VALUE, minValue = Float.MAX_VALUE;
			
			for(int c = 0; c < NB_CHANNELS; ++c) {
				inPos[CHANNEL_AXIS_ID]= c;
				in.setPosition(inPos);
				
				float value = in.get().getRealFloat();
				if(value > maxValue) {
					maxValue = value;
				}
				if(value < minValue) {
					minValue = value;
				}
			}
			
			float result;
			if(maxValue >= Float.MIN_NORMAL && maxValue > minValue)
				result = (maxValue - minValue) / maxValue;
			else
				result = 0;
			
			out.get().setReal(result);
		}

		return output;
	}
}
