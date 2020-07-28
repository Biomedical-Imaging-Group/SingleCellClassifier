package ch.epfl.single_cell_classifier.plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.config.NCModel;
import de.csbdresden.stardist.StarDist2DAccessor;
import ij.IJ;
import net.imagej.Dataset;

@Plugin(type = Command.class, label = "Detect Nuclei (Batch)", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Single Cell Classifier"),
		@Menu(label = "Detection"),
		@Menu(label = "Detect Nuclei (Batch)", weight = 2)
}) 
public class DetectNucleiBatch implements Command {

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

	@Parameter(label="Source Directory", style = "directory")
	private File directory;

	@Parameter(label="Target Directory", style = "directory")
	private File targetDirectory;

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
	
	@Parameter(label="Max Tile Size", min="1", stepSize="1")
	private int maxTileSize = 512;

	@Parameter(label="Verbose")
	private boolean verbose = true;

	@Override
	public void run() {
		try {
			for(File sourceFile : directory.listFiles()){
				if(verbose)
					IJ.log("Processing " + sourceFile.getName());
				
				Dataset source = (Dataset) ioService.open(sourceFile.getAbsolutePath());

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
				Dataset nucleiLabel = (Dataset) futureDetectNuclei.get().getOutput("nucleiLabel");

				String destPath = targetDirectory.getAbsolutePath() + "\\" + sourceFile.getName();
				
				ioService.save(nucleiLabel, destPath);
			}
			
		if(verbose)
			IJ.log("Nuclei Detected");

		} catch (InterruptedException | ExecutionException | IOException e) {
			e.printStackTrace();
		}
	}

}
