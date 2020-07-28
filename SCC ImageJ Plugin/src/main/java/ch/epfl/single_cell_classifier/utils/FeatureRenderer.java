package ch.epfl.single_cell_classifier.utils;

import java.awt.Color;
import java.util.LinkedList;
import java.util.List;

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.measure.CellInformation;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.label.LabelImages;

public class FeatureRenderer {

	public static ImagePlus getLateralCellsGraph(List<CellInformation> cells, int width, int height) {
		ImageProcessor ip = new ColorProcessor(width, height);
		for(CellInformation cell : cells) {
			int x1 = (int)Math.round(cell.getCenter().coord(0));
			int y1 = (int)Math.round(cell.getCenter().coord(1));
			CellInformation leftCell = cell.getLeftCell();
			CellInformation rightCell = cell.getRightCell();

			if(leftCell != null) {
				int x2 = (int)Math.round(leftCell.getCenter().coord(0));
				int y2 = (int)Math.round(leftCell.getCenter().coord(1));

				if(leftCell.getRightCell() == cell || leftCell.getLeftCell() == cell) // If reciprocity
					ip.setColor(Color.WHITE);
				else
					ip.setColor(Color.RED);

				ip.drawLine(x1, y1, x2, y2);
			}
			if(rightCell != null) {
				int x2 = (int)Math.round(rightCell.getCenter().coord(0));
				int y2 = (int)Math.round(rightCell.getCenter().coord(1));

				if(rightCell.getRightCell() == cell || rightCell.getLeftCell() == cell) // If reciprocity
					ip.setColor(Color.WHITE);
				else
					ip.setColor(Color.GREEN);

				ip.drawLine(x1, y1, x2, y2);
			}
		}
		ImagePlus imp = new ImagePlus("Cells lateral neighbours", ip);

		return imp;
	}

	public static ImagePlus getCellsChainGraph(List<LinkedList<CellInformation>> chains, String title, int width, int height) {
		ImageProcessor ip = new ColorProcessor(width, height);
		ip.setColor(Color.WHITE);

		for(LinkedList<CellInformation> chain: chains) {
			CellInformation previousCell = null;
			for(CellInformation cell : chain) {
				if(previousCell != null) {
					int x1 = (int)Math.round(previousCell.getCenter().coord(0));
					int y1 = (int)Math.round(previousCell.getCenter().coord(1));
					int x2 = (int)Math.round(cell.getCenter().coord(0));
					int y2 = (int)Math.round(cell.getCenter().coord(1));

					ip.drawLine(x1, y1, x2, y2);
				}
				previousCell = cell;
			}
		}
		
		ImagePlus imp = new ImagePlus(title, ip);

		return imp;
	}
	
	public static ImagePlus getFeaturesImageFromLabel(ImagePlus label, double[] values, String title) {
		return getFeaturesImageFromLabel(label, values, title, false, 0, 0);
	}

	public static ImagePlus getFeaturesImageFromLabel(ImagePlus label, double[] values, String title, double min, double max) {
		return getFeaturesImageFromLabel(label, values, title, true, min, max);
	}

	private static ImagePlus getFeaturesImageFromLabel(ImagePlus label, double[] values, String title, boolean minMax, double min, double max) {
		FloatProcessor ip = LabelImages.applyLut(label.getProcessor(), values);

		if(minMax) {
			double backgroundValue = (min - max) / 255 * 256 + max;
			replaceNaN(ip, backgroundValue);
			min = backgroundValue;
		}
		ip.setLut(LUTHelper.getIceLUT(0, false));

		ImagePlus imp = new ImagePlus(title, ip);
		if(minMax)
			ip.setMinAndMax(min, max);
		return imp;
	}

	public static ImagePlus getProbabilitiesImage(NCConfig config, ImagePlus label, double[][] probabilities, String title) {
		ImagePlus imp = IJ.createHyperStack(title, label.getWidth(), label.getHeight(), config.getClasses().length, 1, 1, 32);
		
		imp.setDisplayMode(IJ.GRAYSCALE);
		for(int i = 0; i < config.getClasses().length; ++i) {
			imp.setC(i + 1);
			
			FloatProcessor ip = LabelImages.applyLut(label.getProcessor(), probabilities[i]);
			
			double backgroundValue = -1. / 255 * 256 + 1.;
			replaceNaN(ip, backgroundValue);
			
			imp.setProcessor(ip);
			
			imp.setDisplayRange(backgroundValue, 1.);
		}
		imp.setLut(LUTHelper.getIceLUT(0, false));
		
		return imp;
	}
	
	public static void addClassificationResultOverlay(NCConfig config, ImagePlus source, ImagePlus nucleiLabel, List<CellInformation> cells) {
		double[] classificationResult = new double[cells.size()];

		for(int i = 0; i < cells.size(); ++i) {
			classificationResult[i] = cells.get(i).getHighestProbabilityIndex() + 1;
		}

		FloatProcessor ip = LabelImages.applyLut(nucleiLabel.getProcessor(), classificationResult);

		replaceNaN(ip, 0);
		ip.setLut(LUTHelper.getColorsLUT(config.getClassesColor()));
		ip.setMinAndMax(0, config.getClasses().length + 1);
		
		ImageRoi roi = new ImageRoi(0, 0, ip.convertToByte(false));		
		roi.setZeroTransparent(true);
		roi.setOpacity(0.5);
		
		source.setOverlay(new Overlay(roi));
		source.setHideOverlay(false);
	}

	private static void replaceNaN(FloatProcessor ip, double value) {
		for(int y = 0; y < ip.getHeight(); ++y) {
			for(int x = 0; x < ip.getWidth(); ++x) {
				float pixel = ip.getPixelValue(x, y);
				
				if(Float.isNaN(pixel)) {
					ip.putPixelValue(x, y, value);
				}
			}
		}
	}
}
