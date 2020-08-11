# Python script

This folder contains Jupyter notebooks to help you train your own models and analyse your measurements.

## Dependencies version

The scripts have the following dependencies and were tested with the following versions:

- tensorflow: 1.13.1
- keras: 2.3.1
- seaborn: 0.10.0
- eli5: 0.10.1
- pandas: 1.0.3

## Segmentation example

StarDist provide examples to train their model [here](https://github.com/mpicbg-csbd/stardist/tree/master/examples).

Our model was trained on 100 epochs, with 48 rays, images of 320x320 pixels and with a dropout of 0.5.
With GPU enabled it should take about 30 minutes to train on Google Colab.
