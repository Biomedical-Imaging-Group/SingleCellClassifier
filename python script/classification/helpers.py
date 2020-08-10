import pandas as pd
import json
import numpy as np
import tensorflow as tf
from tensorflow import keras
from keras import backend as K
from keras.utils import to_categorical

import shutil
import os

from sklearn.metrics import accuracy_score
from sklearn.metrics import precision_score
from sklearn.metrics import recall_score
from sklearn.metrics import f1_score
from sklearn.metrics import roc_auc_score
from sklearn.metrics import confusion_matrix

CLASS_COLUMN = "Class"

def get_data(path, class_cat, exclude_outliers=False):
    df = pd.read_csv(path)
    del df["Label"]
    if exclude_outliers:
        df = df[((df-df.mean()).abs() < 3 * df.std()).all(axis=1)] #Remove outliers 99.9%
    if not(CLASS_COLUMN in df.columns.values):
        df.insert(0, CLASS_COLUMN, class_cat)
    return df

# ------------------ Preprocessing -----------------------

def process_training_data(dataset, classes, nb_values_per_class):
    headers = dataset.columns.values
    
    mapping = {CLASS_COLUMN: {}}

    for i, class_name in enumerate(classes):
        mapping[CLASS_COLUMN][class_name] = i

    shuffled_dataset = dataset.replace(mapping).sample(frac=1)
    shuffled_dataset = shuffled_dataset.dropna()

    #Normalize
    mean_values = shuffled_dataset.mean().values
    std_values = shuffled_dataset.std().values
    for i, column in enumerate(headers[1:]):
        shuffled_dataset[column] = (shuffled_dataset[column] - mean_values[i + 1]) / std_values[i + 1]

    if nb_values_per_class != -1:
        cur_dataset = pd.DataFrame(columns=headers)
        for i, class_name in enumerate(classes):
            shuffled_class = shuffled_dataset.where(shuffled_dataset[CLASS_COLUMN] == i).dropna()[:nb_values_per_class]
            cur_dataset = cur_dataset.append(shuffled_class, ignore_index=True)
        shuffled_dataset = cur_dataset.sample(frac=1)

    inputs = shuffled_dataset.iloc[:, 1:].values
    targets = shuffled_dataset.iloc[:, 0].values
    targets = to_categorical(targets)
    
    return (inputs, targets, mean_values, std_values)

def process_prediction_data(dataset, classes, normalization_path, random=False):
    headers = dataset.columns.values
    
    mapping = {CLASS_COLUMN: {}}

    for i, class_name in enumerate(classes):
        mapping[CLASS_COLUMN][class_name] = i
    
    res_dataset = dataset.replace(mapping)
    if(random):
        res_dataset = res_dataset.sample(frac=1)
    res_dataset = res_dataset.dropna()
    
    mean_values, std_values = load_normalization(normalization_path)
    for i, column in enumerate(headers[1:]):
        res_dataset[column] = (res_dataset[column] - mean_values[i]) / std_values[i]

    inputs = res_dataset.iloc[:, 1:].values
    targets = res_dataset.iloc[:, 0].values
    targets = to_categorical(targets)
    
    return (inputs, targets)

def resize_inputs(inputs):
    def pad(features, target_size):
        result = np.zeros((features.shape[0], target_size))
        result[:features.shape[0],:features.shape[1]] = features
        return result

    num_features = inputs.shape[1]

    target_size = int(np.ceil(num_features/4) * 4)

    res_inputs = pad(inputs, target_size)
    res_inputs = res_inputs.reshape((-1, 2, 2, int(target_size/4)))
    
    return res_inputs
    
def evaluate(model, inputs, targets):
    prob_targets = model.predict(inputs, verbose=0)
    estimated_targets = model.predict_classes(inputs, verbose=0)
    
    estimated_targets = to_categorical(estimated_targets)
    
    accuracy = accuracy_score(targets, estimated_targets)
    precision = precision_score(targets, estimated_targets, average=None)
    recall = recall_score(targets, estimated_targets, average=None)
    f1 = f1_score(targets, estimated_targets, average=None)
    auc = roc_auc_score(targets, prob_targets, average=None)

    targets = targets.argmax(1)
    estimated_targets = estimated_targets.argmax(1)

    cm = confusion_matrix(targets, estimated_targets)
    cm = cm / cm.sum(axis=1).reshape((cm.shape[0],1))
    
    return accuracy, precision, recall, f1, auc, cm

# ------------------ Model definition --------------------

def get_model(input_size, nb_classes, reshape_included = True, dropout = 0.5):
    model = keras.Sequential()
    if reshape_included:
        model.add(keras.layers.Reshape((input_size,), input_shape=(2, 2, int(input_size/4))))
    model.add(keras.layers.Dense(128, input_shape=(input_size,)))
    model.add(keras.layers.BatchNormalization())
    model.add(keras.layers.ReLU())
    model.add(keras.layers.Dropout(dropout))
    model.add(keras.layers.Dense(64))
    model.add(keras.layers.BatchNormalization())
    model.add(keras.layers.ReLU())
    model.add(keras.layers.Dropout(dropout))
    model.add(keras.layers.Dense(32))
    model.add(keras.layers.BatchNormalization())
    model.add(keras.layers.ReLU())
    model.add(keras.layers.Dropout(dropout))
    model.add(keras.layers.Dense(16))
    model.add(keras.layers.BatchNormalization())
    model.add(keras.layers.ReLU())
    model.add(keras.layers.Dropout(dropout))
    model.add(keras.layers.Dense(nb_classes))
    model.add(keras.layers.Activation("softmax"))
    
    return model

# ---------------- Save and load model -------------------

def save_history(history, export_path):
    new_hist = {}
    for key in list(history.history.keys()):
        if type(history.history[key]) == np.ndarray:
            new_hist[key] = history.history[key].tolist()
        elif type(history.history[key]) == list:
            if type(history.history[key][0]) == np.float64 or type(history.history[key][0]) == np.float32:
                new_hist[key] = list(map(float, history.history[key]))

    with open(export_path, 'w') as file:
        json.dump(new_hist, file)

def save_model(model, mean_values, std_values, export_path):
    if(os.path.exists(export_path)):
        shutil.rmtree(export_path)
    
    signature = tf.saved_model.signature_def_utils.predict_signature_def(
        inputs={'input': model.input}, outputs={'ouput': model.output})
    
    builder = tf.saved_model.builder.SavedModelBuilder(export_path)
    builder.add_meta_graph_and_variables(
        sess=K.get_session(),
        tags=[tf.saved_model.tag_constants.SERVING],
        signature_def_map={
            tf.saved_model.signature_constants.DEFAULT_SERVING_SIGNATURE_DEF_KEY:
                signature
        })
    builder.save()

    normalization = {
        "mean": mean_values[1:].tolist(),
        "stdev": std_values[1:].tolist()
    }

    with open(export_path + '/normalization.json', 'w') as file:
        json.dump(normalization, file, indent=4)

    shutil.make_archive(export_path, "zip", export_path)
    
    model.save(export_path + '/model.h5')
    
def load_normalization(path):
    with open(path, 'r') as file:
        normalization = json.load(file)
    
    return (normalization["mean"], normalization["stdev"])
