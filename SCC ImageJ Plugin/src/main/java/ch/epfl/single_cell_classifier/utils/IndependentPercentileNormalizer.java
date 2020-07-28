package ch.epfl.single_cell_classifier.utils;

import de.csbdresden.csbdeep.normalize.Normalizer;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

public class IndependentPercentileNormalizer<T extends RealType<T> & NativeType<T>>
implements Normalizer {	
	private int nbChannels;
	private int channelAxisId;
	
	private float[] percentiles = new float[] { 3, 99.7f };
	private float[] destValues = new float[] { 0, 1 };
	private boolean clip = false;

	protected float[] min;
	protected float[] max;
	protected float[] factor;
	private float[][] resValues;

	@Override
	public void setup(float[] percentiles, float[] destValues, boolean clip) {
		assert (percentiles.length == 2);
		assert (destValues.length == 2);
		this.percentiles = percentiles;
		this.destValues = destValues;
		this.clip = clip;
	}

	public float normalize( final T val, int channel) {
		if ( clip ) { return Math.max(
				min[channel],
				Math.min( max[channel], ( val.getRealFloat() - resValues[channel][0] ) * factor[channel] + min[channel] ) ); }
		return Math.max( 0, ( val.getRealFloat() - resValues[channel][0] ) * factor[channel] + min[channel] );
	}

	@Override
	public Dataset normalize(Dataset im, OpService opService, DatasetService datasetService) {
		nbChannels = (int) im.getChannels();
		channelAxisId = -1;
		for (int i = 0; i < im.numDimensions(); i++ ) {
			if(im.axis(i).type() == Axes.CHANNEL)
				channelAxisId = i;
		}
		
		min = new float[nbChannels];
		max = new float[nbChannels];
		factor = new float[nbChannels];
		resValues = new float[nbChannels][];
		
		computePercentiles((ImgPlus<T>) im.getImgPlus());

		long[] dims = new long[im.numDimensions()];
		im.dimensions(dims);
		AxisType[] axes = new AxisType[im.numDimensions()];
		for (int i = 0; i < axes.length; i++) {
			axes[i] = im.axis(i).type();
		}

		final Dataset normalizedInput = datasetService.create(new FloatType(), dims, "normalized input", axes);
		final RandomAccess<T> in = (RandomAccess<T>) im.getImgPlus().randomAccess();
		final Cursor<FloatType> out = (Cursor<FloatType>) normalizedInput.getImgPlus().localizingCursor();

		final long[] pos = new long[im.numDimensions()];
		while (out.hasNext()) {
			out.fwd();
			in.setPosition(out);
			out.localize(pos);

			int channel = 0;
			if(channelAxisId != -1)
				channel = (int) pos[channelAxisId];
			
			out.get().set(normalize(in.get(), channel));
		}

		return normalizedInput;
	}

	private void computePercentiles(ImgPlus<T> src) {
		final Cursor< T > cursor = ((IterableInterval)src).cursor();
		int items = 1;
		for (int i = 0; i < src.numDimensions(); i++ ) {
			if(src.axis(i).type().isSpatial())
				items *= src.dimension(i);
		}
		
		final float[][] values = new float[nbChannels][items];
		
		final long[] pos = new long[src.numDimensions()];
		int[] index = new int[nbChannels];
		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(pos);
			
			int channel = 0;
			if(channelAxisId != -1)
				channel = (int) pos[channelAxisId];
			
			values[channel][index[channel]] = cursor.get().getRealFloat();
			index[channel]++;
		}
		
		for(int c = 0; c < nbChannels; ++c) {

			Util.quicksort(values[c]);

			resValues[c] = new float[percentiles.length];
			for (int i = 0; i < percentiles.length; i++ ) {
				resValues[c][i] = values[c][ Math.min(
						values[c].length - 1,
						Math.max(0, Math.round((values[c].length - 1) * percentiles[i] / 100.f)))];
			}
			min[c] = destValues[0];
			max[c] = destValues[1];
			if(resValues[c][1] - resValues[c][0] < 0.0000001) factor[c] = 1;
			else factor[c] = (destValues[1] - destValues[0]) / (resValues[c][1] - resValues[c][0]);
		}
	}

}
