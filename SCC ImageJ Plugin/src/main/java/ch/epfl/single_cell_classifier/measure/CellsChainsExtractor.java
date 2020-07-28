package ch.epfl.single_cell_classifier.measure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class CellsChainsExtractor {
	private List<CellInformation> cells;
	private List<LinkedList<CellInformation>> strongCellsChains = new ArrayList<>();;
	private List<LinkedList<CellInformation>> weakCellsChains = new ArrayList<>();;
	private HashMap<CellInformation, LinkedList<CellInformation>> cellToStrongCellsChains = new HashMap<>();;
	private HashMap<CellInformation, LinkedList<CellInformation>> cellToWeakCellsChains = new HashMap<>();;
	private HashMap<CellInformation, List<CellInformation>> cellToWeakConnections = new HashMap<>();;

	public CellsChainsExtractor(List<CellInformation> cells) {
		this.cells = cells;
		computeStrongCellsChains();
		//computeCellsWeakConnections();
		//computeWeakCellsChains();
	}

	private void computeStrongCellsChains() {
		HashSet<CellInformation> visited = new HashSet<>();
		for(CellInformation cell : cells) {
			if(visited.contains(cell)) continue;

			List<CellInformation> reciprocalCells = cell.getReciprocalLateralNeighbours();

			if(reciprocalCells.size() != 2) {
				LinkedList<CellInformation> cellsChain = new LinkedList<>();
				cellsChain.add(cell);
				visited.add(cell);
				strongCellsChains.add(cellsChain);
				cellToStrongCellsChains.put(cell, cellsChain);

				if(reciprocalCells.size() == 1) {
					CellInformation nextCell = reciprocalCells.get(0);
					while(nextCell != null) {
						CellInformation currentCell = nextCell;
						nextCell = null;
						visited.add(currentCell);
						cellsChain.add(currentCell);
						cellToStrongCellsChains.put(currentCell, cellsChain);

						reciprocalCells = currentCell.getReciprocalLateralNeighbours();

						for(CellInformation recipCell : reciprocalCells) {
							if(!visited.contains(recipCell))
								nextCell = recipCell;
						}
					}
				}
			}

		}

		//Second iteration for circle chains
		for(CellInformation cell : cells) {
			if(visited.contains(cell)) continue;

			List<CellInformation> reciprocalCells = cell.getReciprocalLateralNeighbours();

			LinkedList<CellInformation> cellsChain = new LinkedList<>();
			cellsChain.add(cell);
			visited.add(cell);
			strongCellsChains.add(cellsChain);
			cellToStrongCellsChains.put(cell, cellsChain);

			CellInformation nextCell = reciprocalCells.get(0);
			while(nextCell != null) {
				CellInformation currentCell = nextCell;
				nextCell = null;
				visited.add(currentCell);
				cellsChain.add(currentCell);
				cellToStrongCellsChains.put(currentCell, cellsChain);

				reciprocalCells = currentCell.getReciprocalLateralNeighbours();

				for(CellInformation recipCell : reciprocalCells) {
					if(!visited.contains(recipCell))
						nextCell = recipCell;
				}
			}

		}
	}

	private void computeCellsWeakConnections() {
		for(CellInformation cell : cells) {
			List<CellInformation> cellWeakConnections = new ArrayList<>();
			cellWeakConnections.addAll(cell.getNonReciprocalLateralNeighbours());
			cellToWeakConnections.put(cell, cellWeakConnections);
		}

		for(CellInformation cell : cells) {
			List<CellInformation> nonReciprocalCells = cell.getNonReciprocalLateralNeighbours();
			for(CellInformation nonRecip : nonReciprocalCells) {
				cellToWeakConnections.get(nonRecip).add(cell);
			}
		}
	}

	private void computeWeakCellsChains() {
		HashSet<LinkedList<CellInformation>> visited = new HashSet<>();

		for(LinkedList<CellInformation> cellsChain : strongCellsChains) {
			if(visited.contains(cellsChain)) continue;

			visited.add(cellsChain);
			cellsChain.getFirst().getNonReciprocalLateralNeighbours();

			LinkedList<CellInformation> currentChain = new LinkedList<>();
			weakCellsChains.add(currentChain);
			currentChain.addAll(cellsChain);

			boolean addToFirst = true;
			CellInformation toExpand = cellsChain.getFirst();
			while(toExpand != null) {
				List<CellInformation> cellWeakConnections = toExpand.getNonReciprocalLateralNeighbours();
				toExpand = null;
				int bestNbInChain = 0;
				CellInformation bestCell = null;

				//Find best connection
				for(CellInformation connectedCell : cellWeakConnections) {
					if(connectedCell.getReciprocalLateralNeighbours().size() != 2) { //Check that it is in the extremity of a chain
						LinkedList<CellInformation> chain = cellToStrongCellsChains.get(connectedCell);
						if(visited.contains(chain)) continue;
						
						int nbInChain = chain.size();
						if(nbInChain > bestNbInChain) {
							bestNbInChain = nbInChain;
							bestCell = connectedCell;
						}
					}
				}

				//Merge chains
				if(bestCell != null) {
					LinkedList<CellInformation> toConnect = cellToStrongCellsChains.get(bestCell);
					visited.add(toConnect);

					int insertIndex = 0;
					if(!addToFirst)
						insertIndex = currentChain.size();

					boolean isBestFirst = toConnect.getFirst() == bestCell;

					//Next expand node is in the opposite direction
					if(isBestFirst)
						toExpand = toConnect.getLast();
					else
						toExpand = toConnect.getFirst();

					if(isBestFirst && addToFirst
							|| !isBestFirst && !addToFirst)
						toConnect = reverseList(toConnect);
					currentChain.addAll(insertIndex, toConnect);

				}

				if(toExpand == null && addToFirst)
				{
					addToFirst = false;
					toExpand = cellsChain.getLast();
				}
			}
		}
	}

	private LinkedList<CellInformation> reverseList(LinkedList<CellInformation> list){
		LinkedList<CellInformation> result = new LinkedList<>();
		for (CellInformation cell : list) {
			result.add(0, cell);
		}
		return result;
	}

	public void computeCellsChainsFeatures() {
		HashMap<CellInformation, Integer> nonReciprocalConnectionsToCell = new HashMap<>();
		for(CellInformation cell : cells) {
			List<CellInformation> nonReciprocalCells = cell.getNonReciprocalLateralNeighbours();
			for(CellInformation nonRecip : nonReciprocalCells) {
				int curValue = nonReciprocalConnectionsToCell.getOrDefault(nonRecip, 0);
				nonReciprocalConnectionsToCell.put(nonRecip, curValue + 1);
			}
		}

		for(LinkedList<CellInformation> cellsChain : strongCellsChains) {

			int nonReciprocalNeighboursOfChain = 0;
			CellInformation previousCell = null;
			double totalDistance = 0;
			for(CellInformation cell : cellsChain) {
				nonReciprocalNeighboursOfChain += nonReciprocalConnectionsToCell.getOrDefault(cell, 0);
				if(previousCell != null) {
					totalDistance += previousCell.getCenter().distance(cell.getCenter());
				}
				previousCell = cell;
			}
			double tortuosity = totalDistance / (cellsChain.getFirst().getCenter().distance(cellsChain.getLast().getCenter()));
			if(cellsChain.size() == 1)
				tortuosity = 1;

			for(CellInformation cell : cellsChain) {
				cell.setCellsNumberInChain(cellsChain.size());
				cell.setNonReciprocalNeighboursOfChain(nonReciprocalNeighboursOfChain);
				cell.setCellsChainTortuosity(tortuosity);
			}
		}
	}

	public List<LinkedList<CellInformation>> getStrongCellsChains(){
		return strongCellsChains;
	}

	public List<LinkedList<CellInformation>> getWeakCellsChains(){
		return weakCellsChains;
	}

}
