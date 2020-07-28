package ch.epfl.single_cell_classifier.measure;

import static java.lang.Math.sqrt;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

import ch.epfl.single_cell_classifier.measure.MeasureExtractor.FeatureK;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import us.cornell.delaunay.Pnt;
import us.cornell.delaunay.Triangle;
import us.cornell.delaunay.Triangulation;

public class DelaunayTriangulation {
	private Triangulation triangulation;
	private Triangle initialTriangle;           // Initial triangle
	private HashMap<Pnt, CellInformation> pointToCell;
	private HashMap<CellInformation, List<CellInformation>> cellToSurroundingCells; 

	public DelaunayTriangulation(List<CellInformation> cells, int imgWidth, int imgHeight) {
		pointToCell = new HashMap<>();

		int maxSize = Math.max(imgWidth, imgHeight);

		initialTriangle = new us.cornell.delaunay.Triangle(
				new Pnt(-maxSize, 0),
				new Pnt(maxSize*2, 0),
				new Pnt(maxSize / 2,  maxSize * 3));
		triangulation = new Triangulation(initialTriangle);

		for (CellInformation cell : cells) {
			pointToCell.put(cell.getCenter(), cell);
			triangulation.delaunayPlace(cell.getCenter());
		}

		cellToSurroundingCells = new HashMap<>();
		for(CellInformation cell : cells) {

			cellToSurroundingCells.put(cell, computeSurroundingCells(cell));
		}

		//Remove big global triangle
		ArrayList<Triangle> toRemove = new ArrayList<>();
		for(Pnt p : initialTriangle) {
			for(Triangle t : triangulation) {
				if(t.contains(p)) {
					toRemove.add(t);
				}
			}
		}
		triangulation.removeAll(toRemove);
	}

	private List<CellInformation> computeSurroundingCells(CellInformation cell){
		List<Triangle> surroundingTriangles = triangulation.surroundingTriangles(cell.getCenter(), triangulation.locate(cell.getCenter()));
		List<CellInformation> surroundingCells = new ArrayList<>();
		HashSet<Pnt> visited = new HashSet<>();

		for(Triangle t : surroundingTriangles) {
			for(Pnt p : t) {
				if(p == cell.getCenter()) continue;
				if(!visited.contains(p)) {
					visited.add(p);
					if(pointToCell.containsKey(p)) //Can be null if part of the big triangle
						surroundingCells.add(pointToCell.get(p));
				}
			}
		}

		return surroundingCells;
	}

	public void computeDirectNeighboursInformation(CellInformation cell) {
		List<CellInformation> surroundingCells = cellToSurroundingCells.get(cell);

		double meanDistance = 0;
		for(CellInformation neighbour : surroundingCells) {				
			meanDistance += neighbour.getCenter().distance(cell.getCenter());
		}
		meanDistance /= surroundingCells.size();

		cell.setMeanDistanceToDirectNeighbours(meanDistance);

		double varianceDistance = 0;
		for(CellInformation neighbour : surroundingCells) {
			double distance = neighbour.getCenter().distance(cell.getCenter());
			varianceDistance += Math.pow(distance - meanDistance, 2);
		}
		varianceDistance /= surroundingCells.size();

		cell.setVarianceDistanceToDirectNeighbours(varianceDistance);
	}

	public void computeKNeighboursInformation(int[] k, CellInformation cell) {		
		HashSet<CellInformation> visited = new HashSet<>();
		PriorityQueue<CellInformation> toCheck = new PriorityQueue<>(new Comparator<CellInformation>() {
			@Override
			public int compare(CellInformation c1, CellInformation c2) {
				return Double.compare(c1.getCenter().distance(cell.getCenter()), c2.getCenter().distance(cell.getCenter()));
			}
		});
		toCheck.add(cell);

		int currentK = 0;
		List<CellInformation> neighbours = new ArrayList<CellInformation>();
		double totalDistance = 0;
		double[] neighboursDistance = new double[k[k.length - 1]];
		double totalX = 0;
		double totalY = 0;

		while(!toCheck.isEmpty()) {
			CellInformation currentCell = toCheck.poll();

			if(currentCell != cell) {
				neighbours.add(currentCell);

				neighboursDistance[neighbours.size() - 1] = currentCell.getCenter().distance(cell.getCenter());
				totalDistance += neighboursDistance[neighbours.size() - 1];
				totalX += currentCell.getCenter().coord(0);
				totalY += currentCell.getCenter().coord(1);

				if(neighbours.size() == k[currentK]) {
					cell.setFeatureK(FeatureK.MeanDistanceNormalK, currentK, totalDistance / k[currentK]);
					Pnt centroid = new Pnt(totalX / k[currentK], totalY / k[currentK]);
					cell.setFeatureK(FeatureK.DistanceToCentroid, currentK, cell.getCenter().distance(centroid));

					++currentK;
					if(currentK == k.length)
						break;
				}
			}
			List<CellInformation> surroundingCells = cellToSurroundingCells.get(currentCell);
			for(CellInformation neighbour : surroundingCells) {
				if(!visited.contains(neighbour)) {
					visited.add(neighbour);

					toCheck.add(neighbour);
				}
			}
		}

		double[] varianceDistanceK = new double[k.length];
		for(int i = 0; i < neighbours.size(); ++i) {
			for(int kId = 0; kId < k.length; ++kId) {
				if(k[kId] <= i) continue;
				if(k[kId] > neighbours.size()) continue;
				
				varianceDistanceK[kId] += Math.pow(neighboursDistance[i] - cell.getFeatureK(FeatureK.MeanDistanceNormalK, kId), 2);
			}
		}
		for(int kId = 0; kId < k.length; ++kId) {
			if(k[kId] > neighbours.size()) continue;

			varianceDistanceK[kId] /= k[kId];
			cell.setFeatureK(FeatureK.VarianceDistanceNormalK, kId, varianceDistanceK[kId]);
		}
	}

	public void computeKConnectedNeighboursInformation(int[] k, CellInformation cell) {
		HashSet<CellInformation> visited = new HashSet<>();
		PriorityQueue<CellInformation> toCheck = new PriorityQueue<>(new Comparator<CellInformation>() {
			@Override
			public int compare(CellInformation c1, CellInformation c2) {
				return Double.compare(c1.getCenter().distance(cell.getCenter()), c2.getCenter().distance(cell.getCenter()));
			}
		});
		toCheck.add(cell);

		int currentK = 0;
		List<CellInformation> neighbours = new ArrayList<CellInformation>();
		double totalDistance = 0;
		double totalOrientDif = 0;
		double totalNucleusMinorAxis = 0;
		double totalNucleusMajorAxis = 0;
		double totalNucleusElongation = 0;
		double totalNucleusArea = 0;
		double totalCellMinorAxis = 0;
		double totalCellMajorAxis = 0;
		double totalCellElongation = 0;
		double totalCellArea = 0;
		double totalAreaRatio = 0;
		double[] totalNucleusChannelsFeatures = new double[cell.getNucleusChannelsFeatures().length];
		double[] totalNucleusTexture = new double[TextureAnalyzer.FEATURES_NAME.length];
		double[] totalCytoplasmChannelsFeatures = new double[cell.getNucleusChannelsFeatures().length];
		double[] totalCytoplasmTexture = new double[TextureAnalyzer.FEATURES_NAME.length];
		double[] neighboursDistance = new double[k[k.length - 1]];
		double[] neighboursOrientDif = new double[k[k.length - 1]];
		double[] neighboursNucleusMinorAxis = new double[k[k.length - 1]];
		double[] neighboursNucleusMajorAxis = new double[k[k.length - 1]];
		double[] neighboursNucleusElongation = new double[k[k.length - 1]];
		double[] neighboursNucleusArea = new double[k[k.length - 1]];
		double[] neighboursCellMinorAxis = new double[k[k.length - 1]];
		double[] neighboursCellMajorAxis = new double[k[k.length - 1]];
		double[] neighboursCellElongation = new double[k[k.length - 1]];
		double[] neighboursCellArea = new double[k[k.length - 1]];
		double[] neighboursAreaRatio = new double[k[k.length - 1]];
		double[][] neighboursNucleusChannelsFeature = new double[k[k.length - 1]][cell.getNucleusChannelsFeatures().length];
		double[][] neighboursNucleusTexture = new double[k[k.length - 1]][TextureAnalyzer.FEATURES_NAME.length];
		double[][] neighboursCytoplasmChannelsFeature = new double[k[k.length - 1]][cell.getNucleusChannelsFeatures().length];
		double[][] neighboursCytoplasmTexture = new double[k[k.length - 1]][TextureAnalyzer.FEATURES_NAME.length];

		Pnt[] centroidsForK = new Pnt[k.length];
		double totalX = 0;
		double totalY = 0;

		while(!toCheck.isEmpty()) {
			CellInformation currentCell = toCheck.poll();

			if(currentCell != cell) {
				neighbours.add(currentCell);

				neighboursDistance[neighbours.size() - 1] = currentCell.getCenter().distance(cell.getCenter());
				neighboursOrientDif[neighbours.size() - 1] = Math.abs(cell.getOrientation() - currentCell.getOrientation()) / Math.PI;
				neighboursNucleusMinorAxis[neighbours.size() - 1] = currentCell.getNucleusMinorAxis();
				neighboursNucleusMajorAxis[neighbours.size() - 1] = currentCell.getNucleusMajorAxis();
				neighboursNucleusElongation[neighbours.size() - 1] = currentCell.getNucleusElongation();
				neighboursNucleusArea[neighbours.size() - 1] = currentCell.getNucleusArea();
				neighboursCellMinorAxis[neighbours.size() - 1] = currentCell.getCellMinorAxis();
				neighboursCellMajorAxis[neighbours.size() - 1] = currentCell.getCellMajorAxis();
				neighboursCellElongation[neighbours.size() - 1] = currentCell.getCellElongation();
				neighboursCellArea[neighbours.size() - 1] = currentCell.getCellArea();
				neighboursAreaRatio[neighbours.size() - 1] = currentCell.getAreaRatio();
				neighboursNucleusChannelsFeature[neighbours.size() - 1] = currentCell.getNucleusChannelsFeatures();
				neighboursNucleusTexture[neighbours.size() - 1] = currentCell.getNucleusTextureFeatures();
				neighboursCytoplasmChannelsFeature[neighbours.size() - 1] = currentCell.getCytoplasmChannelsFeatures();
				neighboursCytoplasmTexture[neighbours.size() - 1] = currentCell.getCytoplasmTextureFeatures();
				
				totalDistance += neighboursDistance[neighbours.size() - 1];
				totalOrientDif += neighboursOrientDif[neighbours.size() - 1];
				totalNucleusMinorAxis += neighboursNucleusMinorAxis[neighbours.size() - 1];
				totalNucleusMajorAxis += neighboursNucleusMajorAxis[neighbours.size() - 1];
				totalNucleusElongation += neighboursNucleusElongation[neighbours.size() - 1];
				totalNucleusArea += neighboursNucleusArea[neighbours.size() - 1];
				totalCellMinorAxis += neighboursCellMinorAxis[neighbours.size() - 1];
				totalCellMajorAxis += neighboursCellMajorAxis[neighbours.size() - 1];
				totalCellElongation += neighboursCellElongation[neighbours.size() - 1];
				totalCellArea += neighboursCellArea[neighbours.size() - 1];
				totalAreaRatio += neighboursAreaRatio[neighbours.size() - 1];
				for(int i = 0; i < cell.getNucleusChannelsFeatures().length; ++i) {
					totalNucleusChannelsFeatures[i] += neighboursNucleusChannelsFeature[neighbours.size() - 1][i];
					totalCytoplasmChannelsFeatures[i] += neighboursCytoplasmChannelsFeature[neighbours.size() - 1][i];
				}
				for(int i = 0; i < TextureAnalyzer.FEATURES_NAME.length; ++i) {
					totalNucleusTexture[i] += neighboursNucleusTexture[neighbours.size() - 1][i];
					totalCytoplasmTexture[i] += neighboursCytoplasmTexture[neighbours.size() - 1][i];
				}
				totalX += currentCell.getCenter().coord(0);
				totalY += currentCell.getCenter().coord(1);

				if(neighbours.size() == k[currentK]) {
					cell.setFeatureK(FeatureK.MeanDistanceConnectedK, currentK, totalDistance / k[currentK]);
					cell.setFeatureK(FeatureK.MeanOrientationDif, currentK, totalOrientDif / k[currentK]);
					cell.setFeatureK(FeatureK.MeanNucleusMinorAxis, currentK, totalNucleusMinorAxis / k[currentK]);
					cell.setFeatureK(FeatureK.MeanNucleusMajorAxis, currentK, totalNucleusMajorAxis / k[currentK]);
					cell.setFeatureK(FeatureK.MeanNucleusElongation, currentK, totalNucleusElongation / k[currentK]);
					cell.setFeatureK(FeatureK.MeanNucleusArea, currentK, totalNucleusArea / k[currentK]);
					cell.setFeatureK(FeatureK.MeanCellMinorAxis, currentK, totalCellMinorAxis / k[currentK]);
					cell.setFeatureK(FeatureK.MeanCellMajorAxis, currentK, totalCellMajorAxis / k[currentK]);
					cell.setFeatureK(FeatureK.MeanCellElongation, currentK, totalCellElongation / k[currentK]);
					cell.setFeatureK(FeatureK.MeanCellArea, currentK, totalCellArea / k[currentK]);
					cell.setFeatureK(FeatureK.MeanAreaRatio, currentK, totalAreaRatio / k[currentK]);
					
					double[] resultNucleusChannelsFeatures = new double[cell.getNucleusChannelsFeatures().length];
					double[] resultCytoplasmChannelsFeatures = new double[cell.getNucleusChannelsFeatures().length];
					for(int i = 0; i < cell.getNucleusChannelsFeatures().length; ++i) {
						resultNucleusChannelsFeatures[i] = totalNucleusChannelsFeatures[i] / k[currentK];
						resultCytoplasmChannelsFeatures[i] = totalCytoplasmChannelsFeatures[i] / k[currentK];
					}
					cell.setMeanNucleusChannelsFeaturesUpToKConnectedNeighbours(currentK, resultNucleusChannelsFeatures);
					cell.setMeanCytoplasmChannelsFeaturesUpToKConnectedNeighbours(currentK, resultCytoplasmChannelsFeatures);
					
					double[] resultNucleusTexture = new double[TextureAnalyzer.FEATURES_NAME.length];
					double[] resultCytoplasmTexture = new double[TextureAnalyzer.FEATURES_NAME.length];
					for(int i = 0; i < TextureAnalyzer.FEATURES_NAME.length; ++i) {
						resultNucleusTexture[i] = totalNucleusTexture[i] / k[currentK];
						resultCytoplasmTexture[i] = totalCytoplasmTexture[i] / k[currentK];
					}
					cell.setMeanNucleusTextureUpToKConnectedNeighbours(currentK, resultNucleusTexture);
					cell.setMeanCytoplasmTextureUpToKConnectedNeighbours(currentK, resultCytoplasmTexture);
					
					centroidsForK[currentK] = new Pnt(totalX / k[currentK], totalY / k[currentK]);

					++currentK;
					if(currentK == k.length)
						break;
				}
			}
			List<CellInformation> surroundingCells = cellToSurroundingCells.get(currentCell);
			for(CellInformation neighbour : surroundingCells) {
				double maxDistance = currentCell.getCellMajorAxis() / 2.0 + neighbour.getCellMajorAxis() / 2.0;
				double distance = currentCell.getCenter().distance(neighbour.getCenter());

				//Look only at connected surrounding cells
				if(distance < maxDistance) {
					if(!visited.contains(neighbour)) {
						visited.add(neighbour);
						toCheck.add(neighbour);
					}
				}
			}
		}

		double[] varianceDistanceK = new double[k.length];
		double[] varianceOrientDifK = new double[k.length];
		double[] varianceNucleusMinorAxisK = new double[k.length];
		double[] varianceNucleusMajorAxisK = new double[k.length];
		double[] varianceNucleusElongationK = new double[k.length];
		double[] varianceNucleusAreaK = new double[k.length];
		double[] varianceCellMinorAxisK = new double[k.length];
		double[] varianceCellMajorAxisK = new double[k.length];
		double[] varianceCellElongationK = new double[k.length];
		double[] varianceCellAreaK = new double[k.length];
		double[] varianceAreaRatioK = new double[k.length];
		double[][] varianceNucleusChannelsFeatures = new double[k.length][cell.getNucleusChannelsFeatures().length];
		double[][] varianceNucleusTexture = new double[k.length][TextureAnalyzer.FEATURES_NAME.length];
		double[][] varianceCytoplasmChannelsFeatures = new double[k.length][cell.getNucleusChannelsFeatures().length];
		double[][] varianceCytoplasmTexture = new double[k.length][TextureAnalyzer.FEATURES_NAME.length];

		double[] Ixx = new double[k.length];
		double[] Ixy = new double[k.length];
		double[] Iyy = new double[k.length];

		for(int i = 0; i < neighbours.size(); ++i) {
			for(int kId = 0; kId < k.length; ++kId) {
				if(k[kId] <= i) continue;
				if(k[kId] > neighbours.size()) continue;
				
				varianceDistanceK[kId] += Math.pow(neighboursDistance[i] - cell.getFeatureK(FeatureK.MeanDistanceConnectedK, kId), 2);
				varianceOrientDifK[kId] += Math.pow(neighboursOrientDif[i] - cell.getFeatureK(FeatureK.MeanOrientationDif, kId), 2);
				varianceNucleusMinorAxisK[kId] += Math.pow(neighboursNucleusMinorAxis[i] - cell.getFeatureK(FeatureK.MeanNucleusMinorAxis, kId), 2);
				varianceNucleusMajorAxisK[kId] += Math.pow(neighboursNucleusMajorAxis[i] - cell.getFeatureK(FeatureK.MeanNucleusMajorAxis, kId), 2);
				varianceNucleusElongationK[kId] += Math.pow(neighboursNucleusElongation[i] - cell.getFeatureK(FeatureK.MeanNucleusElongation, kId), 2);
				varianceNucleusAreaK[kId] += Math.pow(neighboursNucleusArea[i] - cell.getFeatureK(FeatureK.MeanNucleusArea, kId), 2);
				varianceCellMinorAxisK[kId] += Math.pow(neighboursCellMinorAxis[i] - cell.getFeatureK(FeatureK.MeanCellMinorAxis, kId), 2);
				varianceCellMajorAxisK[kId] += Math.pow(neighboursCellMajorAxis[i] - cell.getFeatureK(FeatureK.MeanCellMajorAxis, kId), 2);
				varianceCellElongationK[kId] += Math.pow(neighboursCellElongation[i] - cell.getFeatureK(FeatureK.MeanCellElongation, kId), 2);
				varianceCellAreaK[kId] += Math.pow(neighboursCellArea[i] - cell.getFeatureK(FeatureK.MeanCellArea, kId), 2);
				varianceAreaRatioK[kId] += Math.pow(neighboursAreaRatio[i] - cell.getFeatureK(FeatureK.MeanAreaRatio, kId), 2);

				for(int j = 0; j < cell.getNucleusChannelsFeatures().length; ++j) {
					varianceNucleusChannelsFeatures[kId][j] = Math.pow(neighboursNucleusChannelsFeature[i][j] - cell.getMeanNucleusChannelsFeaturesUpToKConnectedNeighbours(kId)[j], 2);
					varianceCytoplasmChannelsFeatures[kId][j] = Math.pow(neighboursCytoplasmChannelsFeature[i][j] - cell.getMeanCytoplasmChannelsFeaturesUpToKConnectedNeighbours(kId)[j], 2);
				}
				for(int j = 0; j < TextureAnalyzer.FEATURES_NAME.length; ++j) {
					varianceNucleusTexture[kId][j] = Math.pow(neighboursNucleusTexture[i][j] - cell.getMeanNucleusTextureUpToKConnectedNeighbours(kId)[j], 2);
					varianceCytoplasmTexture[kId][j] = Math.pow(neighboursCytoplasmTexture[i][j] - cell.getMeanCytoplasmTextureUpToKConnectedNeighbours(kId)[j], 2);
				}
				
				Pnt p = neighbours.get(i).getCenter().subtract(centroidsForK[kId]);
				Ixx[kId] += p.coord(0) * p.coord(0);
				Ixy[kId] += p.coord(0) * p.coord(1);
				Iyy[kId] += p.coord(1) * p.coord(1);
			}
		}
		double sqrt2 = Math.sqrt(2);
		for(int kId = 0; kId < k.length; ++kId) {
			if(k[kId] > neighbours.size()) continue;
			
			varianceDistanceK[kId] /= k[kId];
			varianceOrientDifK[kId] /= k[kId];
			varianceNucleusMinorAxisK[kId] /= k[kId];
			varianceNucleusMajorAxisK[kId] /= k[kId];
			varianceNucleusElongationK[kId] /= k[kId];
			varianceNucleusAreaK[kId] /= k[kId];
			varianceCellMinorAxisK[kId] /= k[kId];
			varianceCellMajorAxisK[kId] /= k[kId];
			varianceCellElongationK[kId] /= k[kId];
			varianceCellAreaK[kId] /= k[kId];
			varianceAreaRatioK[kId] /= k[kId];
			
			cell.setFeatureK(FeatureK.VarianceDistanceConnectedK, kId, varianceDistanceK[kId]);
			cell.setFeatureK(FeatureK.VarianceOrientationDif, kId, varianceOrientDifK[kId]);
			cell.setFeatureK(FeatureK.VarianceNucleusMinorAxis, kId, varianceNucleusMinorAxisK[kId]);
			cell.setFeatureK(FeatureK.VarianceNucleusMajorAxis, kId, varianceNucleusMajorAxisK[kId]);
			cell.setFeatureK(FeatureK.VarianceNucleusElongation, kId, varianceNucleusElongationK[kId]);
			cell.setFeatureK(FeatureK.VarianceNucleusArea, kId, varianceNucleusAreaK[kId]);
			cell.setFeatureK(FeatureK.VarianceCellMinorAxis, kId, varianceCellMinorAxisK[kId]);
			cell.setFeatureK(FeatureK.VarianceCellMajorAxis, kId, varianceCellMajorAxisK[kId]);
			cell.setFeatureK(FeatureK.VarianceCellElongation, kId, varianceCellElongationK[kId]);
			cell.setFeatureK(FeatureK.VarianceCellArea, kId, varianceCellAreaK[kId]);
			cell.setFeatureK(FeatureK.VarianceAreaRatio, kId, varianceAreaRatioK[kId]);
			
			double[] resultNucleusChannelsFeatures = new double[cell.getNucleusChannelsFeatures().length];
			double[] resultCytoplasmChannelsFeatures = new double[cell.getNucleusChannelsFeatures().length];
			for(int i = 0; i < cell.getNucleusChannelsFeatures().length; ++i) {
				resultNucleusChannelsFeatures[i] = varianceNucleusChannelsFeatures[kId][i] / k[kId];
				resultCytoplasmChannelsFeatures[i] = varianceCytoplasmChannelsFeatures[kId][i] / k[kId];
			}
			cell.setVarianceNucleusChannelsFeaturesUpToKConnectedNeighbours(kId, resultNucleusChannelsFeatures);
			cell.setVarianceCytoplasmChannelsFeaturesUpToKConnectedNeighbours(kId, resultCytoplasmChannelsFeatures);
			
			double[] resultNucleusTexture = new double[TextureAnalyzer.FEATURES_NAME.length];
			double[] resultCytoplasmTexture = new double[TextureAnalyzer.FEATURES_NAME.length];
			for(int i = 0; i < TextureAnalyzer.FEATURES_NAME.length; ++i) {
				resultNucleusTexture[i] = varianceNucleusTexture[kId][i] / k[kId];
				resultCytoplasmTexture[i] = varianceCytoplasmTexture[kId][i] / k[kId];
			}
			cell.setVarianceNucleusTextureUpToKConnectedNeighbours(kId, resultNucleusTexture);
			cell.setVarianceCytoplasmTextureUpToKConnectedNeighbours(kId, resultCytoplasmTexture);			
			
			Ixx[kId] /= k[kId];
			Ixy[kId] /= k[kId];
			Iyy[kId] /= k[kId];

			// compute ellipse semi-axes lengths
			double common = sqrt((Ixx[kId] - Iyy[kId]) * (Ixx[kId] - Iyy[kId]) + 4 * Ixy[kId] * Ixy[kId]);
			double ra = sqrt2 * sqrt(Ixx[kId] + Iyy[kId] + common);
			double rb = sqrt2 * sqrt(Ixx[kId] + Iyy[kId] - common);

			// compute ellipse angle in radian
			double theta = Math.atan2(2 * Ixy[kId], Ixx[kId] - Iyy[kId]) / 2;

			cell.setFeatureK(FeatureK.EllipseMajorAxis, kId, ra * 2);
			cell.setFeatureK(FeatureK.EllipseMinorAxis, kId, rb * 2);
			cell.setFeatureK(FeatureK.EllipseElongation, kId, ra / rb);
			cell.setFeatureK(FeatureK.EllipseOrientationDif, kId, Math.abs(cell.getOrientation() - theta) / Math.PI);
			
			double meanEllipseOrientDif = 0;
			double[] neighboursEllipseOrientDif = new double[k[kId]];
			for(int i = 0; i < k[kId]; ++i) {
				neighboursEllipseOrientDif[i] = Math.abs(neighbours.get(i).getOrientation() - theta) / Math.PI;
				meanEllipseOrientDif += neighboursEllipseOrientDif[i];
			}
			meanEllipseOrientDif /= k[kId];
			
			cell.setFeatureK(FeatureK.EllipseMeanOrientationDif, kId, meanEllipseOrientDif);
			
			double varianceEllipseOrientDif = 0;
			for(int i = 0; i < k[kId]; ++i) {
				varianceEllipseOrientDif += Math.pow(neighboursEllipseOrientDif[i] - meanEllipseOrientDif, 2);
			}
			varianceEllipseOrientDif /= k[kId];

			cell.setFeatureK(FeatureK.EllipseVarianceOrientationDif, kId, varianceEllipseOrientDif);
		}
	}

	public void computeLateralCells(CellInformation cell) {
		List<CellInformation> surroundingCells = cellToSurroundingCells.get(cell);

		double minLeftDistance = Double.POSITIVE_INFINITY;
		double minRightDistance = Double.POSITIVE_INFINITY;
		double leftAngle = 0;
		double rightAngle = 0;
		CellInformation leftCell = null;
		CellInformation rightCell = null;

		Pnt A = cell.getCenter();

		double x = A.coord(0) + 5;
		double y = Math.tan(cell.getOrientation() - Math.PI / 2.0) * (x - A.coord(0)) + A.coord(1);
		Pnt C = new Pnt(x, y); //Point on the orthogonal line (on the right side)

		Pnt AC = C.subtract(A);

		for(CellInformation neighbour : surroundingCells) {
			Pnt B = neighbour.getCenter();

			Pnt AB = B.subtract(A);

			double distance = AB.magnitude();
			double angle = AC.angle(AB);

			double absAngle = Math.abs(angle);

			boolean isLeft = absAngle > Math.PI / 2;

			if(isLeft) {
				angle = Math.PI - angle;
				if(angle > Math.PI) //Stay between -pi and pi
					angle -= Math.PI * 2;
				absAngle = Math.abs(angle);
			}

			double distanceToOrtho = Math.sin(absAngle) * distance;

			if(distanceToOrtho > cell.getNucleusMajorAxis() / 2)
				continue; //Is above or below the cell

			if(isLeft) {
				if(distance < minLeftDistance) {
					minLeftDistance = distance;
					leftAngle = angle;
					leftCell = neighbour;
				}
			}else {
				if(distance < minRightDistance) {
					minRightDistance = distance;
					rightAngle = angle;
					rightCell = neighbour;
				}
			}	
		}

		if(leftCell != null) {
			cell.setLeftCell(leftCell, leftAngle);
		}

		if(rightCell != null) {
			cell.setRightCell(rightCell, rightAngle);
		}

	}

	public List<CellInformation> getSurroundingCells(CellInformation cell){
		return cellToSurroundingCells.get(cell);
	}

	public void drawOnProcessor(ByteProcessor ip) {
		Overlay o = new Overlay();
		for(Triangle t : triangulation) {
			float[] xPoints = new float[3];
			float[] yPoints = new float[3];

			int i = 0;
			for(Pnt p : t) {
				xPoints[i] = (float)p.coord(0);
				yPoints[i] = (float)p.coord(1);
				++i;
			}

			PolygonRoi roi = new PolygonRoi(xPoints, yPoints, Roi.POLYGON);
			roi.setStrokeWidth(1);
			roi.setStrokeColor(Color.WHITE);

			o.add(roi);
		}
		ip.drawOverlay(o);
	}
}
