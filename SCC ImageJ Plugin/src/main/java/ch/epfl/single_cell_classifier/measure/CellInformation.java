package ch.epfl.single_cell_classifier.measure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ch.epfl.single_cell_classifier.config.NCConfig;
import ch.epfl.single_cell_classifier.measure.MeasureExtractor.FeatureK;
import inra.ijpb.geometry.Ellipse;
import us.cornell.delaunay.Pnt;

public class CellInformation {
	private int label;
	private Ellipse nucleusEllipse;
	private Ellipse cellEllipse;
	private double nucleusArea;
	private double cellArea;
	private Pnt center;
	private double[] nucleusChannelsFeature;
	private double[] cytoplasmChannelsFeatures;
	private double[] nucleusTextureFeatures;
	private double[] cytoplasmTextureFeatures;
	private double varianceDistanceToDirectNeighbours;
	private double meanDistanceToDirectNeighbours;
	private CellInformation leftCell;
	private double leftCellAngle;
	private CellInformation rightCell;
	private double rightCellAngle;
	private int cellsNumberInChain;
	private int nonReciprocalNeighboursOfChain;
	private double cellsChainTortuosity;
	private List<HashMap<FeatureK, Double>> featureForK;
	private double[][] meanNucleusChannelsFeaturesUpToKConnectedNeighbours;
	private double[][] varianceNucleusChannelsFeaturesUpToKConnectedNeighbours;
	private double[][] meanNucleusTextureUpToKConnectedNeighbours;
	private double[][] varianceNucleusTextureUpToKConnectedNeighbours;
	private double[][] meanCytoplasmChannelsFeaturesUpToKConnectedNeighbours;
	private double[][] varianceCytoplasmChannelsFeaturesUpToKConnectedNeighbours;
	private double[][] meanCytoplasmTextureUpToKConnectedNeighbours;
	private double[][] varianceCytoplasmTextureUpToKConnectedNeighbours;
	private double[] classProbabilities;
	private int maxProbaIndex;

	public CellInformation(int label, NCConfig config) {
		this.label = label;

		featureForK = new ArrayList<>();

		for(int i = 0; i < config.getKNeighbours().length; ++i) {
			featureForK.add(new HashMap<>());
		}

		meanNucleusChannelsFeaturesUpToKConnectedNeighbours = new double[config.getKNeighbours().length][2 * config.getNucleiChannelsFactor().length];
		varianceNucleusChannelsFeaturesUpToKConnectedNeighbours = new double[config.getKNeighbours().length][2 * config.getNucleiChannelsFactor().length];
		meanNucleusTextureUpToKConnectedNeighbours = new double[config.getKNeighbours().length][TextureAnalyzer.FEATURES_NAME.length];
		varianceNucleusTextureUpToKConnectedNeighbours = new double[config.getKNeighbours().length][TextureAnalyzer.FEATURES_NAME.length];
		meanCytoplasmChannelsFeaturesUpToKConnectedNeighbours = new double[config.getKNeighbours().length][2 * config.getNucleiChannelsFactor().length];
		varianceCytoplasmChannelsFeaturesUpToKConnectedNeighbours = new double[config.getKNeighbours().length][2 * config.getNucleiChannelsFactor().length];
		meanCytoplasmTextureUpToKConnectedNeighbours = new double[config.getKNeighbours().length][TextureAnalyzer.FEATURES_NAME.length];
		varianceCytoplasmTextureUpToKConnectedNeighbours = new double[config.getKNeighbours().length][TextureAnalyzer.FEATURES_NAME.length];
	}

	public int getLabel() {
		return label;
	}

	public void setNucleusEllipse(Ellipse nucleusEllipse) {
		this.nucleusEllipse = nucleusEllipse;
	}

	public double getNucleusMinorAxis() {
		return nucleusEllipse.radius2() * 2;
	}

	public double getNucleusMajorAxis() {
		return nucleusEllipse.radius1() * 2;
	}

	public double getNucleusElongation() {
		return nucleusEllipse.radius1() / nucleusEllipse.radius2();
	}

	public void setCellEllipse(Ellipse cellEllipse) {
		this.cellEllipse = cellEllipse;
	}

	public double getCellMinorAxis() {
		return cellEllipse.radius2() * 2;
	}

	public double getCellMajorAxis() {
		return cellEllipse.radius1() * 2;
	}

	public double getCellElongation() {
		return cellEllipse.radius1() / cellEllipse.radius2();
	}

	public void setNucleusArea(double nucleuArea) {
		this.nucleusArea = nucleuArea;
	}

	public double getNucleusArea() {
		return nucleusArea;
	}

	public void setCellArea(double cellArea) {
		this.cellArea = cellArea;
	}

	public double getCellArea() {
		return cellArea;
	}

	public double getAreaRatio() {
		return cellArea / nucleusArea;
	}

	public void setCenter(Pnt center) {
		this.center = center;
	}

	public Pnt getCenter() {
		return center;
	}

	/**
	 * @return the orientation of the nucleus major axis in radian
	 */
	public double getOrientation() {
		return nucleusEllipse.orientation() / 180 * Math.PI;
	}

	public void setCytoplasmChannelsFeatures(double[] channelsFeatures) {
		cytoplasmChannelsFeatures = channelsFeatures;
	}

	public double[] getCytoplasmChannelsFeatures() {
		return cytoplasmChannelsFeatures;
	}

	public void setNucleusTextureFeatures(double[] textureFeatures) {
		nucleusTextureFeatures = textureFeatures;
	}

	public double[] getNucleusTextureFeatures() {
		return nucleusTextureFeatures;
	}

	public void setCytoplasmTextureFeatures(double[] textureFeatures) {
		cytoplasmTextureFeatures = textureFeatures;
	}

	public double[] getCytoplasmTextureFeatures() {
		return cytoplasmTextureFeatures;
	}

	public void setMeanDistanceToDirectNeighbours(double meanDistance) {
		meanDistanceToDirectNeighbours = meanDistance;
	}

	public double getMeanDistanceToDirectNeighbours() {
		return meanDistanceToDirectNeighbours;
	}

	public void setVarianceDistanceToDirectNeighbours(double variance) {
		varianceDistanceToDirectNeighbours = variance;
	}

	public double getVarianceDistanceToDirectNeighbours() {
		return varianceDistanceToDirectNeighbours;
	}
	
	public void setLeftCell(CellInformation leftCell, double angle) {
		this.leftCell = leftCell;
		leftCellAngle = angle;
	}

	public void setRightCell(CellInformation rightCell, double angle) {
		this.rightCell = rightCell;
		rightCellAngle = angle;
	}

	public CellInformation getLeftCell() {
		return leftCell;
	}

	public CellInformation getRightCell() {
		return rightCell;
	}

	public double getLeftAngle() {
		return leftCellAngle;
	}

	public double getRightAngle() {
		return rightCellAngle;
	}

	public double getLateralNeighboursAlignment() {
		if (leftCell == null || rightCell == null) // Need 3 point for alignment
			return 0;
		double angleDif = Math.abs(leftCellAngle + rightCellAngle); // If alignment should be 0, max of pi
		return 1.0 - (angleDif / Math.PI);
	}

	public double getLateralNeighboursOrientationDif() {
		double leftDif = 0;
		if (leftCell != null)
			leftDif = Math.abs(getOrientation() - leftCell.getOrientation()) / Math.PI;
		double rightDif = 0;
		if (rightCell != null)
			rightDif = Math.abs(getOrientation() - rightCell.getOrientation()) / Math.PI;
		if (leftCell == null)
			return rightDif;
		if (rightCell == null)
			return leftDif;
		if (leftCell == null && rightCell == null)
			return 1;

		return (leftDif + rightDif) / 2;
	}

	public double getLateralNeighboursMeanDistance() {
		double totalDist = 0;
		if (leftCell != null)
			totalDist += leftCell.getCenter().distance(this.getCenter());
		if (rightCell != null)
			totalDist += rightCell.getCenter().distance(this.getCenter());
		if (leftCell != null && rightCell != null)
			totalDist /= 2;

		return totalDist;
	}

	public List<CellInformation> getReciprocalLateralNeighbours() {
		List<CellInformation> reciprocalCells = new ArrayList<>();

		if (leftCell != null) {
			if (leftCell.getRightCell() == this || leftCell.getLeftCell() == this) { // If reciprocity
				reciprocalCells.add(leftCell);
			}
		}
		if (rightCell != null) {
			if (rightCell.getRightCell() == this || rightCell.getLeftCell() == this) { // If reciprocity
				reciprocalCells.add(rightCell);
			}
		}

		return reciprocalCells;
	}

	public List<CellInformation> getNonReciprocalLateralNeighbours() {
		List<CellInformation> nonReciprocalCells = new ArrayList<>();

		if (leftCell != null) {
			if (leftCell.getRightCell() != this && leftCell.getLeftCell() != this) { // If non reciprocity
				nonReciprocalCells.add(leftCell);
			}
		}
		if (rightCell != null) {
			if (rightCell.getRightCell() != this && rightCell.getLeftCell() != this) { // If non reciprocity
				nonReciprocalCells.add(rightCell);
			}
		}

		return nonReciprocalCells;
	}

	public void setCellsNumberInChain(int nb) {
		cellsNumberInChain = nb;
	}

	public int getCellsNumberInChain() {
		return cellsNumberInChain;
	}

	public void setNonReciprocalNeighboursOfChain(int nb) {
		nonReciprocalNeighboursOfChain = nb;
	}

	public int getNonReciprocalNeighboursOfChain() {
		return nonReciprocalNeighboursOfChain;
	}

	public double getNonReciprocityRatioInChain() {
		return nonReciprocalNeighboursOfChain / (double) cellsNumberInChain;
	}

	public void setCellsChainTortuosity(double tortuosity) {
		cellsChainTortuosity = tortuosity;
	}

	public double getCellsChainTortuosity() {
		return cellsChainTortuosity;
	}
	
	public void setFeatureK(FeatureK featureK, int kId, double value) {
		featureForK.get(kId).put(featureK, value);
	}

	public double getFeatureK(FeatureK featureK, int kId) {
		return featureForK.get(kId).getOrDefault(featureK, featureK.getDefaultValue());
	}

	public void setMeanNucleusChannelsFeaturesUpToKConnectedNeighbours(int kId, double[] rgbFactor) {
		meanNucleusChannelsFeaturesUpToKConnectedNeighbours[kId] = rgbFactor;
	}

	public double[] getMeanNucleusChannelsFeaturesUpToKConnectedNeighbours(int kId) {
		return meanNucleusChannelsFeaturesUpToKConnectedNeighbours[kId];
	}

	public void setVarianceNucleusChannelsFeaturesUpToKConnectedNeighbours(int kId, double[] rgbFactor) {
		varianceNucleusChannelsFeaturesUpToKConnectedNeighbours[kId] = rgbFactor;
	}

	public double[] getVarianceNucleusChannelsFeaturesUpToKConnectedNeighbours(int kId) {
		return varianceNucleusChannelsFeaturesUpToKConnectedNeighbours[kId];
	}

	public void setMeanNucleusTextureUpToKConnectedNeighbours(int kId, double[] texture) {
		meanNucleusTextureUpToKConnectedNeighbours[kId] = texture;
	}

	public double[] getMeanNucleusTextureUpToKConnectedNeighbours(int kId) {
		return meanNucleusTextureUpToKConnectedNeighbours[kId];
	}

	public void setVarianceNucleusTextureUpToKConnectedNeighbours(int kId, double[] texture) {
		varianceNucleusTextureUpToKConnectedNeighbours[kId] = texture;
	}

	public double[] getVarianceNucleusTextureUpToKConnectedNeighbours(int kId) {
		return varianceNucleusTextureUpToKConnectedNeighbours[kId];
	}

	public void setMeanCytoplasmChannelsFeaturesUpToKConnectedNeighbours(int kId, double[] rgbFactor) {
		meanCytoplasmChannelsFeaturesUpToKConnectedNeighbours[kId] = rgbFactor;
	}

	public double[] getMeanCytoplasmChannelsFeaturesUpToKConnectedNeighbours(int kId) {
		return meanCytoplasmChannelsFeaturesUpToKConnectedNeighbours[kId];
	}

	public void setVarianceCytoplasmChannelsFeaturesUpToKConnectedNeighbours(int kId, double[] rgbFactor) {
		varianceCytoplasmChannelsFeaturesUpToKConnectedNeighbours[kId] = rgbFactor;
	}

	public double[] getVarianceCytoplasmChannelsFeaturesUpToKConnectedNeighbours(int kId) {
		return varianceCytoplasmChannelsFeaturesUpToKConnectedNeighbours[kId];
	}

	public void setMeanCytoplasmTextureUpToKConnectedNeighbours(int kId, double[] texture) {
		meanCytoplasmTextureUpToKConnectedNeighbours[kId] = texture;
	}

	public double[] getMeanCytoplasmTextureUpToKConnectedNeighbours(int kId) {
		return meanCytoplasmTextureUpToKConnectedNeighbours[kId];
	}

	public void setVarianceCytoplasmTextureUpToKConnectedNeighbours(int kId, double[] texture) {
		varianceCytoplasmTextureUpToKConnectedNeighbours[kId] = texture;
	}

	public double[] getVarianceCytoplasmTextureUpToKConnectedNeighbours(int kId) {
		return varianceCytoplasmTextureUpToKConnectedNeighbours[kId];
	}

	public void setNucleusChannelsFeatures(double[] channelsFeatures) {
		nucleusChannelsFeature = channelsFeatures;
	}

	public double[] getNucleusChannelsFeatures() {
		return nucleusChannelsFeature;
	}

	private int getNbClassificationFeatures() {
		int temp = getNucleusChannelsFeatures().length + getNucleusTextureFeatures().length;
		return 18 +	2 * temp + 
				(FeatureK.values().length + 4 * temp) * featureForK.size();
	}

	public double[] getClassificationFeatures() {
		double[] classificationFeatures = new double[getNbClassificationFeatures()];

		int pos = 0;
		classificationFeatures[pos++] = getNucleusMinorAxis();
		classificationFeatures[pos++] = getNucleusMajorAxis();
		classificationFeatures[pos++] = getNucleusElongation();
		classificationFeatures[pos++] = getCellMinorAxis();
		classificationFeatures[pos++] = getCellMajorAxis();
		classificationFeatures[pos++] = getCellElongation();
		classificationFeatures[pos++] = getNucleusArea();
		classificationFeatures[pos++] = getCellArea();
		classificationFeatures[pos++] = getAreaRatio();
		classificationFeatures[pos++] = getMeanDistanceToDirectNeighbours();
		classificationFeatures[pos++] = getVarianceDistanceToDirectNeighbours();
		classificationFeatures[pos++] = getLateralNeighboursAlignment();
		classificationFeatures[pos++] = getLateralNeighboursOrientationDif();
		classificationFeatures[pos++] = getLateralNeighboursMeanDistance();
		classificationFeatures[pos++] = getCellsNumberInChain();
		classificationFeatures[pos++] = getNonReciprocalNeighboursOfChain();
		classificationFeatures[pos++] = getNonReciprocityRatioInChain();
		classificationFeatures[pos++] = getCellsChainTortuosity();
		for (int i = 0; i < getNucleusChannelsFeatures().length; ++i) {
			classificationFeatures[pos++] = getNucleusChannelsFeatures()[i];
		}
		for (int i = 0; i < getNucleusTextureFeatures().length; ++i) {
			classificationFeatures[pos++] = getNucleusTextureFeatures()[i];
		}
		for (int i = 0; i < getCytoplasmChannelsFeatures().length; ++i) {
			classificationFeatures[pos++] = getCytoplasmChannelsFeatures()[i];
		}
		for (int i = 0; i < getCytoplasmTextureFeatures().length; ++i) {
			classificationFeatures[pos++] = getCytoplasmTextureFeatures()[i];
		}
		for (int i = 0; i < featureForK.size(); ++i) {
			for(FeatureK featureK : FeatureK.values()) {
				classificationFeatures[pos++] = getFeatureK(featureK, i);
			}
			for (int j = 0; j < getMeanNucleusChannelsFeaturesUpToKConnectedNeighbours(i).length; ++j) {
				classificationFeatures[pos++] = getMeanNucleusChannelsFeaturesUpToKConnectedNeighbours(i)[j];
			}
			for (int j = 0; j < getVarianceNucleusChannelsFeaturesUpToKConnectedNeighbours(i).length; ++j) {
				classificationFeatures[pos++] = getVarianceNucleusChannelsFeaturesUpToKConnectedNeighbours(i)[j];
			}
			for (int j = 0; j < getMeanNucleusTextureUpToKConnectedNeighbours(i).length; ++j) {
				classificationFeatures[pos++] = getMeanNucleusTextureUpToKConnectedNeighbours(i)[j];
			}
			for (int j = 0; j < getVarianceNucleusTextureUpToKConnectedNeighbours(i).length; ++j) {
				classificationFeatures[pos++] = getVarianceNucleusTextureUpToKConnectedNeighbours(i)[j];
			}
			for (int j = 0; j < getMeanCytoplasmChannelsFeaturesUpToKConnectedNeighbours(i).length; ++j) {
				classificationFeatures[pos++] = getMeanCytoplasmChannelsFeaturesUpToKConnectedNeighbours(i)[j];
			}
			for (int j = 0; j < getVarianceCytoplasmChannelsFeaturesUpToKConnectedNeighbours(i).length; ++j) {
				classificationFeatures[pos++] = getVarianceCytoplasmChannelsFeaturesUpToKConnectedNeighbours(i)[j];
			}
			for (int j = 0; j < getMeanCytoplasmTextureUpToKConnectedNeighbours(i).length; ++j) {
				classificationFeatures[pos++] = getMeanCytoplasmTextureUpToKConnectedNeighbours(i)[j];
			}
			for (int j = 0; j < getVarianceCytoplasmTextureUpToKConnectedNeighbours(i).length; ++j) {
				classificationFeatures[pos++] = getVarianceCytoplasmTextureUpToKConnectedNeighbours(i)[j];
			}
		}

		return classificationFeatures;
	}

	public void setClassProbabilities(double[] proba) {
		classProbabilities = proba;

		maxProbaIndex = 0;
		double maxProba = -1;
		for(int i = 0; i < classProbabilities.length; ++i) {
			if(classProbabilities[i] > maxProba) {
				maxProba = classProbabilities[i];
				maxProbaIndex = i;
			}
		}
	}

	public double[] getClassProbabilities() {
		return classProbabilities;
	}

	public int getHighestProbabilityIndex() {
		return maxProbaIndex;
	}
}
