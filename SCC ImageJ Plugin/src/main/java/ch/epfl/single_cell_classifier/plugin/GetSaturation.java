package ch.epfl.single_cell_classifier.plugin;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ch.epfl.single_cell_classifier.utils.RGBToSaturationConverter;
import net.imagej.Dataset;
import net.imagej.DatasetService;

@Plugin(type = Command.class, label = "Get Saturation", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Single Cell Classifier"),
		@Menu(label = "Utilities"),
		@Menu(label = "Get Saturation", weight = 4)
}) 
public class GetSaturation implements Command{
	@Parameter
	protected DatasetService datasetService;

	@Parameter(label="Source")
	private Dataset source;
	
	@Parameter(label="Source saturation", type = ItemIO.OUTPUT)
	private Dataset sourceSaturation;

	@Override
	public void run() {
		RGBToSaturationConverter satConverter = new RGBToSaturationConverter();
		sourceSaturation = satConverter.convert(source, datasetService);
	}
}
