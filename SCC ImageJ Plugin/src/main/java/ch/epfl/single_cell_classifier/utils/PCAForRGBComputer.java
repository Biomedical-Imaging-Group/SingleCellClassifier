package ch.epfl.single_cell_classifier.utils;

import ij.ImagePlus;

/**
 * Compute Gray level of RGB image using PCA
 */
public class PCAForRGBComputer{

	/**
	 * Compute the best rgb factor by PCA
	 * @param imp
	 * @param mask
	 * @param label (if equal to -1, accept all but 0)
	 * @return the best rgb factor
	 */
	public static double[] computeRGBFactor(ImagePlus imp, ImagePlus mask, boolean invertMask) {
		double meanR = 0;
		double meanG = 0;
		double meanB = 0;
		double nbPixels = 0;
		for(int y = 0; y < imp.getHeight(); ++y) {
			for(int x = 0; x < imp.getWidth(); ++x) {
				int[] rgb = imp.getPixel(x, y);

				if(mask != null) {
					int maskPixel = mask.getProcessor().getPixel(x, y);
					if(!invertMask && maskPixel == 0)
						continue;
					if(invertMask && maskPixel != 0)
						continue;
				}

				meanR += rgb[0];
				meanG += rgb[1];
				meanB += rgb[2];
				++nbPixels;
			}
		}

		if(nbPixels == 0) return new double[] {0, 0, 0};

		meanR /= nbPixels;
		meanG /= nbPixels;
		meanB /= nbPixels;

		// Compute XT * X matrix values
		double rr = 0;
		double gg = 0;
		double bb = 0;
		double rg = 0;
		double gb = 0;
		double rb = 0;

		for(int y = 0; y < imp.getHeight(); ++y) {
			for(int x = 0; x < imp.getWidth(); ++x) {
				int[] rgb = imp.getPixel(x, y);

				if(mask != null) {
					int maskPixel = mask.getProcessor().getPixel(x, y);
					if(!invertMask && maskPixel == 0)
						continue;
					if(invertMask && maskPixel != 0)
						continue;
				}

				double r = rgb[0] - meanR;
				double g = rgb[1] - meanG;
				double b = rgb[2] - meanB;

				rr += r * r;
				gg += g * g;
				bb += b * b;
				rg += r * g;
				gb += g * b;
				rb += r * b;
			}
		}

		// Compute the maximum eigenvalue of the matrix
		double q = (rr + gg + bb) / 3;
		double p1 = rg * rg + gb * gb + rb * rb;
		double p2 = Math.pow(rr - q, 2.0) + Math.pow(gg - q, 2.0) + Math.pow(bb - q, 2.0) + 2 * p1;
		double p = Math.sqrt(p2 / 6);

		double b11 = (1 / p) * (rr - q);
		double b22 = (1 / p) * (gg - q);
		double b33 = (1 / p) * (bb - q);
		double b12 = (1 / p) * rg;
		double b23 = (1 / p) * gb;
		double b13 = (1 / p) * rb;

		double detB = b11 * (b22 * b33 - b23 * b23) - b12 * (b12 * b33 - b23 * b13) + b13 * (b12 * b23 - b22 * b13);
		double r = detB / 2;

		double phi;
		if (r <= -1) {
			phi = Math.PI / 3;
		} else if (r >= 1) {
			phi = 0;
		} else {
			phi = Math.acos(r) / 3;
		}

		double maxEig = q + 2 * p + Math.cos(phi);

		// Compute the eigenvector associated to the eigenvalue (the solution)
		double dif12 = rg / (rr - maxEig);

		double gFactor = (rb * dif12 - gb) / ((gg - maxEig) - rg * dif12);
		double rFactor = -(rg * gFactor + rb) / (rr - maxEig);
		double bFactor = 1;

		double norm = rFactor + gFactor + bFactor;

		rFactor /= norm;
		gFactor /= norm;
		bFactor /= norm;

		return new double[]{
				rFactor,
				gFactor,
				bFactor
		};
	}
}
