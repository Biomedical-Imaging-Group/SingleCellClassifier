package ch.epfl.single_cell_classifier.plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.io.IOService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;

import ch.epfl.single_cell_classifier.classification.Classifier;
import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.config.NCModel;
import ch.epfl.single_cell_classifier.measure.CellInformation;
import ch.epfl.single_cell_classifier.measure.MeasureExtractor;
import ch.epfl.single_cell_classifier.utils.FeatureRenderer;
import de.csbdresden.stardist.StarDist2DAccessor;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

@Plugin(type = Command.class, label = "Classify (Batch)", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Single Cell Classifier"),
		@Menu(label = "Classification"),
		@Menu(label = "Classify (Batch)", weight = 2)
}) 
public class ClassifyBatch implements Command {

	@Parameter
	protected DatasetService datasetService;

	@Parameter
	protected OpService opService;

	@Parameter
	protected CommandService command;

	@Parameter
	protected IOService ioService;

	private static final String CONFIG_CHOICE_FILE = "Config (.json) from File";

	@Parameter(label="Config", choices = {
		NCConfig.CONFIG_HUMAN_MOUSE_HE_PDX,
		CONFIG_CHOICE_FILE
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String configChoice;

	@Parameter(label="Source Directory (*)", style = "directory")
	private File sourceDirectory;

	@Parameter(label="Nuclei Labels Directory", style = "directory", required = false)
	private File nucleiLabelsDirectory;

	@Parameter(label="Cells Labels Directory", style = "directory", required = false)
	private File cellsLabelsDirectory;

	@Parameter(label="Result Probabilities Directory", style = "directory", required = false)
	private File resultProbabilitiesDirectory;

	@Parameter(label="Measurements File (.csv)", style = "save", required = false)
	private File measurementsFile;

	@Parameter(label="Result Directory (*)", style = "directory")
	private File resultDirectory;
	
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
	private String segmentationModelChoice = MODEL_CHOICE_CONFIG;

	@Parameter(label="Segmentation Model (.zip) from File", required = false)
	private File segmentationModelFile;

	@Parameter(label="Classification Model", choices = {
		MODEL_CHOICE_CONFIG,
		NCModel.MODEL_CLASSIFICATION_HUMAN_MOUSE_HE_PDX,
		MODEL_CHOICE_FILE
	}, style=ChoiceWidget.LIST_BOX_STYLE)
	private String classificationModelChoice = MODEL_CHOICE_CONFIG;

	@Parameter(label="Classification Model (.zip) from File", required = false)
	private File classificationModelFile;

	@Parameter(label="Max Tile Size", min="1", stepSize="1")
	private int maxTileSize = 512;
	
	@Parameter(label="Verbose")
	private boolean verbose = true;

	@Parameter(label="output", type = ItemIO.OUTPUT)
	private Dataset output;

	@Override
	public void run() {
		try {
			for(File sourceFile : sourceDirectory.listFiles()){
				if(verbose)
					IJ.log("Processing " + sourceFile.getName());
				
				Dataset source = (Dataset) ioService.open(sourceFile.getAbsolutePath());

				Dataset nucleiLabel;
				if(nucleiLabelsDirectory == null) {
					if(verbose)
						IJ.log("--- Detecting Nuclei ");

					final HashMap<String, Object> paramsDetectNuclei = new HashMap<>();
					paramsDetectNuclei.put("configChoice", configChoice);
					paramsDetectNuclei.put("input", source);
					paramsDetectNuclei.put("outputType", DetectNuclei.OUTPUT_LABEL_IMAGE);
					paramsDetectNuclei.put("configFile", configFile);
					paramsDetectNuclei.put("segmentationModelChoice", segmentationModelChoice);
					paramsDetectNuclei.put("segmentationModelFile", segmentationModelFile);
					paramsDetectNuclei.put("configFile", configFile);
					paramsDetectNuclei.put("maxTileSize", maxTileSize);
					final Future<CommandModule> futureDetectNuclei = command.run(DetectNuclei.class, false, paramsDetectNuclei);
					nucleiLabel = (Dataset) futureDetectNuclei.get().getOutput("nucleiLabel");
				}else {
					if(verbose)
						IJ.log("--- Loading Nuclei ");
					String nucleiPath = nucleiLabelsDirectory.getAbsolutePath() + "\\" + sourceFile.getName();
					nucleiLabel = (Dataset) ioService.open(nucleiPath);
				}

				Dataset cellsLabel;
				if(cellsLabelsDirectory == null) {
					if(verbose)
						IJ.log("--- Detecting Cells ");

					final HashMap<String, Object> paramsDetectCells = new HashMap<>();
					paramsDetectCells.put("configChoice", configChoice);
					paramsDetectCells.put("source", source);
					paramsDetectCells.put("nucleiLabel", nucleiLabel);
					paramsDetectCells.put("configFile", configFile);
					final Future<CommandModule> futureDetectCells = command.run(DetectCells.class, false, paramsDetectCells);
					cellsLabel = (Dataset) futureDetectCells.get().getOutput("cellsLabel");
				}else {
					if(verbose)
						IJ.log("--- Loading Cells ");
					String cellsPath = cellsLabelsDirectory.getAbsolutePath() + "\\" + sourceFile.getName();
					cellsLabel = (Dataset) ioService.open(cellsPath);
				}

				if(verbose)
					IJ.log("--- Measuring Information ");

				NCConfig config = getConfig();
				File classificationModel = getModelFile(config);

				ImagePlus sourceIp = ImageJFunctions.wrap((RandomAccessibleInterval<UnsignedByteType>) source.getImgPlus(), "source");
				ImagePlus nucleiLabelIp = ImageJFunctions.wrap((RandomAccessibleInterval<UnsignedShortType>) nucleiLabel.getImgPlus(), "nuclei label");
				ImagePlus cellsLabelIp = ImageJFunctions.wrap((RandomAccessibleInterval<UnsignedShortType>) cellsLabel.getImgPlus(), "cells label");

				MeasureExtractor measureExtractor = new MeasureExtractor(config, sourceIp, nucleiLabelIp, cellsLabelIp, opService);
				List<CellInformation> cells = measureExtractor.getCells();

				if(verbose)
					IJ.log("--- Classifying Cells ");

				Classifier.classify(classificationModel, cells, command, datasetService);

				if(resultProbabilitiesDirectory != null) {
					double[][] classProbabilities = Classifier.getClassProbabilities(config, cells);
					ImagePlus probabilitiesIp = FeatureRenderer.getProbabilitiesImage(config, nucleiLabelIp, classProbabilities, source.getName() + " probabilities");
					
					String probPath = resultProbabilitiesDirectory.getAbsolutePath() + "\\" + sourceFile.getName();
					IJ.save(probabilitiesIp, probPath);
				}
				
				sourceIp = new CompositeImage(sourceIp, IJ.COMPOSITE);
				FeatureRenderer.addClassificationResultOverlay(config, sourceIp, nucleiLabelIp, cells);

				String resultPath = resultDirectory.getAbsolutePath() + "\\" + sourceFile.getName();
				IJ.save(sourceIp, resultPath);
				
				if(measurementsFile != null)
					measureExtractor.writeMeasuresInFile(config, measurementsFile, true, true, true);
			}
			
		if(verbose)
			IJ.log("Cells Classified");

		} catch (InterruptedException | ExecutionException | IOException e) {
			e.printStackTrace();
		}
	}

	private File getModelFile(NCConfig config) {
		if(classificationModelChoice.equals(MODEL_CHOICE_CONFIG))
			classificationModelChoice = config.getClassificationModelName();
		switch(classificationModelChoice) {
		case MODEL_CHOICE_FILE:
			return classificationModelFile;
		default:
			return NCModel.CLASSIFICATION_MODELS.get(classificationModelChoice).getModelFile();
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
