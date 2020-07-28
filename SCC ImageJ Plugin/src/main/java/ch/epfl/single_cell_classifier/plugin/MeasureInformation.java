package ch.epfl.single_cell_classifier.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.config.NCModel;
import ch.epfl.single_cell_classifier.measure.CellInformation;
import ch.epfl.single_cell_classifier.measure.MeasureExtractor;
import ch.epfl.single_cell_classifier.measure.TextureAnalyzer;
import ch.epfl.single_cell_classifier.measure.MeasureExtractor.FeatureK;
import ch.epfl.single_cell_classifier.utils.FeatureRenderer;
import de.csbdresden.stardist.StarDist2DAccessor;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import net.imagej.Dataset;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

@Plugin(type = Command.class, label = "Measure Information", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Single Cell Classifier"),
		@Menu(label = "Measurement"),
		@Menu(label = "Measure Information", weight = 1)
}) 
public class MeasureInformation implements Command{
	@Parameter
	protected OpService opService;

	@Parameter
	protected CommandService command;

	private static final String CONFIG_CHOICE_FILE = "Config (.json) from File";

	@Parameter(label="Config", choices = {
		NCConfig.CONFIG_HUMAN_MOUSE_HE_PDX,
		CONFIG_CHOICE_FILE
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String configChoice;

	@Parameter(label="Source")
	private Dataset source;

	public static final String OUTPUT_TABLE = "Results Table";
	public static final String OUTPUT_FILE = "File";
	public static final String OUTPUT_BOTH = "Both";

	@Parameter(label="Output Type", choices={OUTPUT_TABLE, OUTPUT_FILE, OUTPUT_BOTH}, style=ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	private String outputType = OUTPUT_TABLE;

	@Parameter(label="Output File (.csv)", style = "save", required = false)
	private File outputFile;

	@Parameter(label="Append To File")
	private boolean append = true;

	@Parameter(label="Show Nuclei ROI")
	private boolean showNucleiROI = true;

	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>Generate images</b></html>")
	private final String generateMsg = "<html><br/><hr width='100'></html>";

	@Parameter(label="Ellipse info")
	private boolean generateEllipseInfo = false;

	@Parameter(label="Area info")
	private boolean generateAreaInfo = false;

	@Parameter(label="Channels info")
	private boolean generateChannelsInfo = false;

	@Parameter(label="Texture info")
	private boolean generateTextureInfo = false;

	@Parameter(label="Delaunay graph")
	private boolean generateDelaunayGraph = false;

	@Parameter(label="Distance to direct neighbours")
	private boolean generateDistanceToDirectNeighbours = false;

	@Parameter(label="Nuclei lateral neighbours info")
	private boolean generateNucleiLateralNeighboursInfo = false;
	
	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>Advanced Options</b></html>")
	private final String advMsg = "<html><br/><hr width='100'></html>";

	@Parameter(label=CONFIG_CHOICE_FILE, required = false)
	private File configFile;

	private static final String MODEL_CHOICE_CONFIG = "Model of config";
	private static final String MODEL_CHOICE_FILE = "Model (.zip) from File";
	
	@Parameter(label="Segmentation Model", choices = {
		MODEL_CHOICE_CONFIG,
		NCModel.MODEL_SEGMENTATION_HUMAN_MOUSE_HE_PDX,
		StarDist2DAccessor.MODEL_DSB2018_HEAVY_AUGMENTATION,
		StarDist2DAccessor.MODEL_HE_HEAVY_AUGMENTATION,
		StarDist2DAccessor.MODEL_DSB2018_PAPER,
		MODEL_CHOICE_FILE
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String segmentationModelChoice;

	@Parameter(label="Segmentation Model (.zip) from File", required = false)
	private File segmentationModelFile;
	
	@Parameter(label="Max Tile Size", min="1", stepSize="1")
	private int maxTileSize = 512;

	@Parameter(label="Verbose")
	private boolean verbose = true;
	
	@Override
	public void run() {
		if(verbose)
			IJ.log("Processing " + source.getName());

		ImagePlus sourceIp = ImageJFunctions.wrap((RandomAccessibleInterval<? extends NumericType>) source.getImgPlus(), "source");

		try {
			if(verbose)
				IJ.log("--- Detecting Nuclei ");

			final HashMap<String, Object> paramsDetectNuclei = new HashMap<>();
			paramsDetectNuclei.put("configChoice", configChoice);
			paramsDetectNuclei.put("input", source);
			paramsDetectNuclei.put("outputType", showNucleiROI ? DetectNuclei.OUTPUT_BOTH : DetectNuclei.OUTPUT_LABEL_IMAGE);
			paramsDetectNuclei.put("configFile", configFile);
			paramsDetectNuclei.put("segmentationModelChoice", segmentationModelChoice);
			paramsDetectNuclei.put("segmentationModelFile", segmentationModelFile);
			paramsDetectNuclei.put("configFile", configFile);
			paramsDetectNuclei.put("maxTileSize", maxTileSize);
			final Future<CommandModule> futureDetectNuclei = command.run(DetectNuclei.class, false, paramsDetectNuclei);
			Dataset nucleiLabel = (Dataset) futureDetectNuclei.get().getOutput("nucleiLabel");

			if(verbose)
				IJ.log("--- Detecting Cells ");

			final HashMap<String, Object> paramsDetectCells = new HashMap<>();
			paramsDetectCells.put("configChoice", configChoice);
			paramsDetectCells.put("source", source);
			paramsDetectCells.put("nucleiLabel", nucleiLabel);
			paramsDetectCells.put("configFile", configFile);
			final Future<CommandModule> futureDetectCells = command.run(DetectCells.class, false, paramsDetectCells);
			Dataset cellsLabel = (Dataset) futureDetectCells.get().getOutput("cellsLabel");

			ImagePlus nucleiLabelIp = ImageJFunctions.wrap((RandomAccessibleInterval<UnsignedShortType>) nucleiLabel.getImgPlus(), "nuclei label");
			ImagePlus cellsLabelIp = ImageJFunctions.wrap((RandomAccessibleInterval<UnsignedShortType>) cellsLabel.getImgPlus(), "cells label");

			if(verbose)
				IJ.log("--- Measuring Information ");

			NCConfig config = getConfig();
			
			MeasureExtractor measureExtractor = new MeasureExtractor(config, sourceIp, nucleiLabelIp, cellsLabelIp, opService);

			List<CellInformation> cells = measureExtractor.getCells();

			ResultsTable resultsTable = new ResultsTable();
			measureExtractor.addMeasureToResultsTable(config, resultsTable, false);

			int pos = 1;
			if(generateEllipseInfo) {
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos), resultsTable.getColumnHeading(pos)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 1), resultsTable.getColumnHeading(pos + 1)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 2), resultsTable.getColumnHeading(pos + 2)).show();
				FeatureRenderer.getFeaturesImageFromLabel(cellsLabelIp, resultsTable.getColumnAsDoubles(pos + 3), resultsTable.getColumnHeading(pos + 3)).show();
				FeatureRenderer.getFeaturesImageFromLabel(cellsLabelIp, resultsTable.getColumnAsDoubles(pos + 4), resultsTable.getColumnHeading(pos + 4)).show();
				FeatureRenderer.getFeaturesImageFromLabel(cellsLabelIp, resultsTable.getColumnAsDoubles(pos + 5), resultsTable.getColumnHeading(pos + 5)).show();
			}
			pos += 6;
			if(generateAreaInfo) {
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos), resultsTable.getColumnHeading(pos)).show();
				FeatureRenderer.getFeaturesImageFromLabel(cellsLabelIp, resultsTable.getColumnAsDoubles(pos + 1), resultsTable.getColumnHeading(pos + 1)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 2), resultsTable.getColumnHeading(pos + 2)).show();
			}
			pos += 3;
			if(generateDelaunayGraph) {
				ByteProcessor ip = new ByteProcessor(sourceIp.getWidth(), sourceIp.getHeight());
				measureExtractor.getDelaunayTriangulation().drawOnProcessor(ip);
				ImagePlus imp = new ImagePlus("Delaunay graph", ip);
				imp.show();
			}
			if(generateDistanceToDirectNeighbours) {
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos),  resultsTable.getColumnHeading(pos)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 1), resultsTable.getColumnHeading(pos)).show();
			}
			pos += 2;
			if(generateNucleiLateralNeighboursInfo) {
				FeatureRenderer.getLateralCellsGraph(cells, sourceIp.getWidth(), sourceIp.getHeight()).show();
				FeatureRenderer.getCellsChainGraph(measureExtractor.getCellsChainsExtractor().getStrongCellsChains(), "Strong cells chains", sourceIp.getWidth(), sourceIp.getHeight()).show();
				FeatureRenderer.getCellsChainGraph(measureExtractor.getCellsChainsExtractor().getWeakCellsChains(), "Weak cells chains", sourceIp.getWidth(), sourceIp.getHeight()).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos), resultsTable.getColumnHeading(pos)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 1), resultsTable.getColumnHeading(pos + 1)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 2), resultsTable.getColumnHeading(pos + 2)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 3), resultsTable.getColumnHeading(pos + 3)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 4), resultsTable.getColumnHeading(pos + 4)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 5), resultsTable.getColumnHeading(pos + 5)).show();
				FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + 6), resultsTable.getColumnHeading(pos + 6)).show();
			}
			pos += 7;
			if(generateChannelsInfo) {
				for(int c = 0; c < 2 * config.getNucleiChannelsFactor().length; ++c) {
					FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + c), resultsTable.getColumnHeading(pos + c)).show();	
				}
			}
			pos += 2 * config.getNucleiChannelsFactor().length;
			if(generateTextureInfo) {
				for(int i = 0; i < TextureAnalyzer.FEATURES_NAME.length; ++i) {
					FeatureRenderer.getFeaturesImageFromLabel(nucleiLabelIp, resultsTable.getColumnAsDoubles(pos + i), resultsTable.getColumnHeading(pos + i)).show();	
				}
			}
			pos += TextureAnalyzer.FEATURES_NAME.length;
			if(generateChannelsInfo) {
				for(int c = 0; c < 2 * config.getNucleiChannelsFactor().length; ++c) {
					FeatureRenderer.getFeaturesImageFromLabel(cellsLabelIp, resultsTable.getColumnAsDoubles(pos + c), resultsTable.getColumnHeading(pos + c)).show();	
				}
			}
			pos += 2 * config.getNucleiChannelsFactor().length;
			if(generateTextureInfo) {
				for(int i = 0; i < TextureAnalyzer.FEATURES_NAME.length; ++i) {
					FeatureRenderer.getFeaturesImageFromLabel(cellsLabelIp, resultsTable.getColumnAsDoubles(pos + i), resultsTable.getColumnHeading(pos + i)).show();	
				}
			}
			pos += TextureAnalyzer.FEATURES_NAME.length;
			pos += (FeatureK.values().length + 4 * (config.getNucleiChannelsFactor().length + TextureAnalyzer.FEATURES_NAME.length)) * config.getKNeighbours().length;

			if(outputType.equals(OUTPUT_FILE) || outputType.equals(OUTPUT_BOTH))
				measureExtractor.writeMeasuresInFile(config, outputFile, append, false, false);

			if(outputType.equals(OUTPUT_TABLE) || outputType.equals(OUTPUT_BOTH))
				resultsTable.show("Nuclei and cells measurements");
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}

	private NCConfig getConfig() {
		switch(configChoice) {
		case CONFIG_CHOICE_FILE:
			return new NCConfig(configFile);
		default:
			return NCConfig.CONFIGS.get(configChoice);
		}
	}
}
