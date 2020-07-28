package ch.epfl.single_cell_classifier.plugin;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ch.epfl.single_cell_classifier.utils.PCAForRGBComputer;
import ij.ImagePlus;
import ij.io.Opener;
import ij.measure.ResultsTable;

@Plugin(type = Command.class, label = "Compute Best RGB Factor", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Single Cell Classifier"),
		@Menu(label = "Utilities"),
		@Menu(label = "Compute Best RGB Factor", weight = 2)
}) 
public class ComputeBestRGBFactor implements Command{
	@Parameter(label="Source Directory (*)", style = "directory")
	private File directory;

	@Parameter(label="Mask Directory", style = "directory", required = false)
	private File maskDirectory;
	
	@Parameter(label="Invert Mask")
	private boolean invertMask = false;
	
	@Override
	public void run() {				
		ResultsTable resultsTable = new ResultsTable();
		for(File sourceFile : directory.listFiles()){
			final ImagePlus imp = new Opener().openImage(sourceFile.getAbsolutePath());
			
			ImagePlus mask = null;
			if(maskDirectory != null) {
				String maskPath = maskDirectory.getAbsolutePath() + "\\" + sourceFile.getName();
				mask = new Opener().openImage(maskPath);
			}

			double[] rgbFactor = PCAForRGBComputer.computeRGBFactor(imp, mask, invertMask);

			resultsTable.incrementCounter();
			resultsTable.addValue("Red", rgbFactor[0]);
			resultsTable.addValue("Green", rgbFactor[1]);
			resultsTable.addValue("Blue", rgbFactor[2]);
		}

		resultsTable.show("RGB Factors");
	}
}
