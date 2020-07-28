package ch.epfl.single_cell_classifier.measure;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.ArrayUtils;

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.utils.ChannelsToGrayConverter;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import inra.ijpb.geometry.Ellipse;
import inra.ijpb.measure.IntrinsicVolumes2D;
import inra.ijpb.measure.region2d.Centroid;
import inra.ijpb.measure.region2d.InertiaEllipse;
import net.imagej.ops.OpService;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import us.cornell.delaunay.Pnt;

public class MeasureExtractor {	
	private List<CellInformation> cells;
	private DelaunayTriangulation delaunayTriangulation;
	private CellsChainsExtractor cellsChainsExtractor;
	private String[] classificationHeaders;

	public enum FeatureK{
		DistanceToCentroid("Distance to centroid up to ", " neighbours", 0),
		MeanDistanceNormalK("Mean distance up to ", " neighbours", 0),
		VarianceDistanceNormalK("Variance distance up to ", " neighbours", 0),
		MeanDistanceConnectedK("Mean distance up to ", " connected neighbours", 0),
		VarianceDistanceConnectedK("Variance distance up to ", " connected neighbours", 0),
		MeanOrientationDif("Mean orientation difference up to ", " connected neighbours", 1),
		VarianceOrientationDif("Variance orientation difference up to ", " connected neighbours", 1),
		MeanNucleusMinorAxis("Mean nucleus minor axis up to ", " connected neighbours", 0),
		VarianceNucleusMinorAxis("Variance nucleus minor axis up to ", " connected neighbours", 0),
		MeanNucleusMajorAxis("Mean nucleus major axis up to ", " connected neighbours", 0),
		VarianceNucleusMajorAxis("Variance nucleus major axis up to ", " connected neighbours", 0),
		MeanNucleusElongation("Mean nucleus elongation up to ", " connected neighbours", 0),
		VarianceNucleusElongation("Variance nucleus elongation up to ", " connected neighbours", 0),
		MeanNucleusArea("Mean nucleus area up to ", " connected neighbours", 0),
		VarianceNucleusArea("Variance nucleus area up to ", " connected neighbours", 0),
		MeanCellMinorAxis("Mean cell minor axis up to ", " connected neighbours", 0),
		VarianceCellMinorAxis("Variance cell minor axis up to ", " connected neighbours", 0),
		MeanCellMajorAxis("Mean cell major axis up to ", " connected neighbours", 0),
		VarianceCellMajorAxis("Variance cell major axis up to ", " connected neighbours", 0),
		MeanCellElongation("Mean cell elongation up to ", " connected neighbours", 0),
		VarianceCellElongation("Variance cell elongation up to ", " connected neighbours", 0),
		MeanCellArea("Mean cell area up to ", " connected neighbours", 0),
		VarianceCellArea("Variance cell area up to ", " connected neighbours", 0),
		MeanAreaRatio("Mean area ratio up to ", " connected neighbours", 0),
		VarianceAreaRatio("Variance area ratio up to ", " connected neighbours", 0),
		EllipseMinorAxis("Ellipse minor axis up to ", " connected neighbours", 0),
		EllipseMajorAxis("Ellipse major axis up to ", " connected neighbours", 0),
		EllipseElongation("Ellipse elongation up to ", " connected neighbours", 0),
		EllipseOrientationDif("Ellipse orientation difference up to ", " connected neighbours", 1),
		EllipseMeanOrientationDif("Ellipse mean orientation difference up to ", " connected neighbours", 1),
		EllipseVarianceOrientationDif("Ellipse variance orientation difference up to ", " connected neighbours", 1),
		;

		private String beforeString;
		private String afterString;
		private double defaultValue;

		FeatureK(String beforeString, String afterString, double defaultValue){
			this.beforeString = beforeString;
			this.afterString = afterString;
			this.defaultValue = defaultValue;
		}

		public String getName(int k) {
			return beforeString + k + afterString;
		}

		public double getDefaultValue() {
			return defaultValue;
		}
	}
	
	public MeasureExtractor(NCConfig config, ImagePlus source, ImagePlus nucleiLabel, ImagePlus cellsLabel, OpService opService) {
		initClassificationHeaders(config);
		
		int maxLabel = (int) nucleiLabel.getStatistics().max;

		int[] labels = new int[maxLabel];
		for(int label = 1; label <= maxLabel; ++label) {
			labels[label - 1] = label;
		}

		ImagePlus nucleiGraySource = ChannelsToGrayConverter.convert(source, config.getNucleiChannelsFactor());
		ImagePlus cytoplasmGraySource = ChannelsToGrayConverter.convert(source, config.getCytoplasmsChannelsFactor());

		ImagePlus cytoplasmLabel = getCytoplasmLabels(nucleiLabel, cellsLabel, opService);
		
		ImageProcessor nucleiLabelIp = nucleiLabel.getProcessor();
		ImageProcessor cellsLabelIp = cellsLabel.getProcessor();
		ImageProcessor cytoplasmLabelIp = cytoplasmLabel.getProcessor();
		ImageProcessor nucleiGraySourceIp = nucleiGraySource.getProcessor();
		ImageProcessor cytoplasmGraySourceIp = cytoplasmGraySource.getProcessor();
		
		Ellipse[] nucleiEllipses = InertiaEllipse.inertiaEllipses(nucleiLabelIp, labels, new Calibration());
		Ellipse[] cellsEllipses = InertiaEllipse.inertiaEllipses(cellsLabelIp, labels, new Calibration());

		double[] nucleiArea = IntrinsicVolumes2D.areas(nucleiLabelIp, labels, new Calibration());
		double[] cellsArea = IntrinsicVolumes2D.areas(cellsLabelIp, labels, new Calibration());

		double[][] nucleiChannelsFeatures = ChannelsAnalyzer.computeChannelsFeatures(source, nucleiLabelIp, maxLabel);
		double[][] cytoplasmChannelsFeatures = ChannelsAnalyzer.computeChannelsFeatures(source, cytoplasmLabelIp, maxLabel);
		
		double[][] nucleiTextureFeatures = TextureAnalyzer.computeTextureFeatures(nucleiGraySourceIp, nucleiLabelIp, maxLabel);
		double[][] cytoplasmTextureFeatures = TextureAnalyzer.computeTextureFeatures(cytoplasmGraySourceIp, cytoplasmLabelIp, maxLabel);
		
		double[][] centroids = Centroid.centroids(nucleiLabelIp, labels);
		
		cells = new ArrayList<>();

		for(int label = 1; label <= maxLabel; ++label) {
			CellInformation cell = new CellInformation(label, config);
			cell.setNucleusEllipse(nucleiEllipses[label - 1]);
			cell.setCellEllipse(cellsEllipses[label - 1]);
			cell.setNucleusArea(nucleiArea[label - 1]);
			cell.setCellArea(cellsArea[label - 1]);
			cell.setCenter(new Pnt(centroids[label - 1]));

			cell.setNucleusChannelsFeatures(nucleiChannelsFeatures[label - 1]);
			cell.setCytoplasmChannelsFeatures(cytoplasmChannelsFeatures[label - 1]);
			
			cell.setNucleusTextureFeatures(nucleiTextureFeatures[label - 1]);
			cell.setCytoplasmTextureFeatures(cytoplasmTextureFeatures[label - 1]);

			cells.add(cell);
		}
		
		delaunayTriangulation = new DelaunayTriangulation(cells, nucleiLabel.getWidth(), nucleiLabel.getHeight());
		
		for(CellInformation cell : cells) {
			delaunayTriangulation.computeDirectNeighboursInformation(cell);
			delaunayTriangulation.computeKNeighboursInformation(config.getKNeighbours(), cell);
			delaunayTriangulation.computeKConnectedNeighboursInformation(config.getKNeighbours(), cell);
			delaunayTriangulation.computeLateralCells(cell);			
		}
		
		cellsChainsExtractor = new CellsChainsExtractor(cells);
		cellsChainsExtractor.computeCellsChainsFeatures();		
	}

	private void initClassificationHeaders(NCConfig config) {
		List<String> headers = new ArrayList<String>();
		headers.add("Nucleus minor Axis");
		headers.add("Nucleus major Axis");
		headers.add("Nucleus elongation");
		headers.add("Cell minor Axis");
		headers.add("Cell major Axis");
		headers.add("Cell elongation");
		headers.add("Nucleus area");
		headers.add("Cell area");
		headers.add("Area ratio");

		headers.add("Direct neighbours mean distance");
		headers.add("Direct neighbours variance distance");
		headers.add("Lateral neighbours alignment");
		headers.add("Lateral neighbours orientation differences");
		headers.add("Lateral neighbours mean distance");
		headers.add("Cells number in chain");
		headers.add("Cells chain non reciprocal neighbours");
		headers.add("Cells chain non reciprocal neighbours ratio");
		headers.add("Cells chain tortuosity");

		for(int c = 1; c <= config.getNucleiChannelsFactor().length; ++c) {
			headers.add("Nucleus mean value channel " + c);
			headers.add("Nucleus variance value channel " + c);
		}
		for(int i = 0; i <  TextureAnalyzer.FEATURES_NAME.length; ++i) {
			headers.add("Nucleus " + TextureAnalyzer.FEATURES_NAME[i].toLowerCase());
		}
		
		for(int c = 1; c <= config.getNucleiChannelsFactor().length; ++c) {
			headers.add("Cytoplasm mean value channel " + c);
			headers.add("Cytoplasm variance value channel " + c);
		}
		for(int i = 0; i <  TextureAnalyzer.FEATURES_NAME.length; ++i) {
			headers.add("Cytoplasm " + TextureAnalyzer.FEATURES_NAME[i].toLowerCase());
		}
		
		for(int i = 0; i < config.getKNeighbours().length; ++i) {
			for(FeatureK feature : FeatureK.values()) {
				headers.add(feature.getName(config.getKNeighbours()[i]));
			}
			for(int c = 1; c <= config.getNucleiChannelsFactor().length; ++c) {
				headers.add("Mean nucleus mean value channel " + c + " up to " + config.getKNeighbours()[i] + " connected neighbours");
				headers.add("Mean nucleus variance value channel " + c + " up to " + config.getKNeighbours()[i] + " connected neighbours");
			}
			for(int c = 1; c <= config.getNucleiChannelsFactor().length; ++c) {
				headers.add("Variance nucleus mean value channel " + c + " up to " + config.getKNeighbours()[i] + " connected neighbours");
				headers.add("Variance nucleus variance value channel " + c + " up to " + config.getKNeighbours()[i] + " connected neighbours");
			}

			for(int j = 0; j <  TextureAnalyzer.FEATURES_NAME.length; ++j) {
				headers.add("Mean nucleus " + TextureAnalyzer.FEATURES_NAME[j].toLowerCase() + " up to " + config.getKNeighbours()[i] + " connected neighbours");
			}
			for(int j = 0; j <  TextureAnalyzer.FEATURES_NAME.length; ++j) {
				headers.add("Variance nucleus " + TextureAnalyzer.FEATURES_NAME[j].toLowerCase() + " up to " + config.getKNeighbours()[i] + " connected neighbours");
			}
			for(int c = 1; c <= config.getNucleiChannelsFactor().length; ++c) {
				headers.add("Mean cytoplasm mean value channel " + c + " up to " + config.getKNeighbours()[i] + " connected neighbours");
				headers.add("Mean cytoplasm variance value channel " + c + " up to " + config.getKNeighbours()[i] + " connected neighbours");
			}
			for(int c = 1; c <= config.getNucleiChannelsFactor().length; ++c) {
				headers.add("Variance cytoplasm mean value channel " + c + " up to " + config.getKNeighbours()[i] + " connected neighbours");
				headers.add("Variance cytoplasm variance value channel " + c + " up to " + config.getKNeighbours()[i] + " connected neighbours");
			}
			for(int j = 0; j <  TextureAnalyzer.FEATURES_NAME.length; ++j) {
				headers.add("Mean cytoplasm " + TextureAnalyzer.FEATURES_NAME[j].toLowerCase() + " up to " + config.getKNeighbours()[i] + " connected neighbours");
			}
			for(int j = 0; j <  TextureAnalyzer.FEATURES_NAME.length; ++j) {
				headers.add("Variance cytoplasm " + TextureAnalyzer.FEATURES_NAME[j].toLowerCase() + " up to " + config.getKNeighbours()[i] + " connected neighbours");
			}
		}

		classificationHeaders = headers.toArray(new String[0]);
	}

	private ImagePlus getCytoplasmLabels(ImagePlus nucleiLabel, ImagePlus cellsLabel, OpService opService) {
		IterableInterval<BitType> nucleiMask = opService.threshold().apply((IterableInterval)ImagePlusAdapter.wrapImgPlus(nucleiLabel), new UnsignedShortType(0));
		IterableInterval<BitType> invertedNucleiMask = opService.copy().iterableInterval(nucleiMask);
		opService.image().invert(invertedNucleiMask, nucleiMask);

		IterableInterval<UnsignedShortType> cellsLabelInter = (IterableInterval)ImagePlusAdapter.wrapImgPlus(cellsLabel);
		IterableInterval<UnsignedShortType> cytoplasmLabel = opService.math().multiply(cellsLabelInter, (IterableInterval<UnsignedShortType>)opService.convert().uint16(invertedNucleiMask));

		return ImageJFunctions.wrap((RandomAccessibleInterval<UnsignedShortType>) cytoplasmLabel, "cytoplasm label");
	}
	
	public List<CellInformation> getCells() {
		return cells;
	}

	public DelaunayTriangulation getDelaunayTriangulation() {
		return delaunayTriangulation;
	}

	public CellsChainsExtractor getCellsChainsExtractor() {
		return cellsChainsExtractor;
	}

	public ResultsTable addMeasureToResultsTable(NCConfig config, ResultsTable resultsTable, boolean includeClassificationResult) {		
		for(CellInformation cell : cells) {
			resultsTable.incrementCounter();
			resultsTable.addValue("Label", cell.getLabel());

			if(includeClassificationResult) {
				resultsTable.addValue("Class", config.getClasses()[cell.getHighestProbabilityIndex()]);
				for(int i = 0; i < config.getClasses().length; ++i) {
					resultsTable.addValue(config.getClasses()[i] + " probability", cell.getClassProbabilities()[i]);					
				}
			}

			double[] classificationFeatures = cell.getClassificationFeatures();
			for(int i = 0; i < classificationFeatures.length; ++i) {
				resultsTable.addValue(classificationHeaders[i], classificationFeatures[i]);
			}
		}

		return resultsTable;
	}

	public void writeMeasuresInFile(NCConfig config, File targetFile, boolean append, boolean includeClassificationResult, boolean includeClassificationProbabilities) {
		try {
			if(!targetFile.exists())
				append = false; //Will write header if append is false or if new file
			BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile, append));

			int probabilitiesToAdd = 0;
			if(includeClassificationProbabilities)
				probabilitiesToAdd += config.getClasses().length;

			boolean isFirst = true;
			for(CellInformation cell : cells) {
				if(isFirst && !append) {
					if(includeClassificationResult) {
						String[] headers = new String[2 + probabilitiesToAdd];
						headers[0] = "Label";
						headers[1] = "Class";
						for(int i = 0; i < probabilitiesToAdd; ++i) {
							headers[2+i] = config.getClasses()[i] + " probability";
						}
						writer.write(String.join(",", ArrayUtils.addAll(headers, classificationHeaders)));
					}
					else
						writer.write(String.join(",", ArrayUtils.addAll(new String[] {"Label"}, classificationHeaders)));						
					writer.newLine();
				}
				
				double[] classificationFeatures = cell.getClassificationFeatures();
				
				int featSize = classificationFeatures.length + 1; // features and label
				if(includeClassificationResult)
					featSize += 1 + probabilitiesToAdd; // class and probabilities
				
				String[] strFeat = new String[featSize];
				int pos = 0;
				strFeat[pos++] = String.valueOf(cell.getLabel());
				if(includeClassificationResult) {
					strFeat[pos++] = String.valueOf(config.getClasses()[cell.getHighestProbabilityIndex()]);

					for(int i = 0; i < probabilitiesToAdd; ++i) {
						strFeat[pos++] = String.format(Locale.US, "%.4e", cell.getClassProbabilities()[i]);
					}
				}
				for(int i = 0; i < classificationFeatures.length; ++i) {
					strFeat[pos++] = String.format(Locale.US, "%.4e", classificationFeatures[i]);
				}
				
				writer.write(String.join(",", strFeat));
				writer.newLine();

				isFirst = false;
			}
			
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
