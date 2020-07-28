package ch.epfl.single_cell_classifier.classification;

import ch.epfl.single_cell_classifier.measure.MeasureExtractor;

public class PostProcessor {
	public static void process(MeasureExtractor measureExtractor) {
		/*
		List<CellInformation> cells = measureExtractor.getCells();
		double[] newProbabilities = new double[cells.size()];
		for(int i = 0; i < cells.size(); ++i) {
			CellInformation cell = cells.get(i);
			List<CellInformation> surroundingCells = measureExtractor.getDelaunayTriangulation().getSurroundingCells(cell);

			double proba = cell.getHumanProbability();
			int nbValidNeighbours = 1;
			for(CellInformation neighbour : surroundingCells) {
				double maxDistance = cell.getCellMajorAxis() / 2.0 + neighbour.getCellMajorAxis() / 2.0;
				
				if(cell.getCenter().distance(neighbour.getCenter()) < maxDistance) {
					proba += neighbour.getHumanProbability();
					++nbValidNeighbours;
				}
			}
			proba /= nbValidNeighbours;
			
			newProbabilities[i] = proba;
		}

		for(int i = 0; i < cells.size(); ++i) {
			cells.get(i).setHumanProbability(newProbabilities[i]);
		}
		*/
	}
}
