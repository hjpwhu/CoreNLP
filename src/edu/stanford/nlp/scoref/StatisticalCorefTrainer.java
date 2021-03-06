package edu.stanford.nlp.scoref;

import java.io.File;
import java.util.Properties;

import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.scoref.StatisticalCorefProperties.Dataset;
import edu.stanford.nlp.util.StringUtils;

public class StatisticalCorefTrainer {
  public static final String CLASSIFICATION_MODEL = "classification";
  public static final String RANKING_MODEL = "ranking";
  public static final String ANAPHORICITY_MODEL = "anaphoricity";
  public static final String CLUSTERING_MODEL_NAME = "clusterer";
  public static final String EXTRACTED_FEATURES_NAME = "features";

  public static String trainingPath;
  public static String pairwiseModelsPath;
  public static String clusteringModelsPath;

  public static String predictionsName;
  public static String datasetFile;
  public static String goldClustersFile;
  public static String wordCountsFile;
  public static String mentionTypesFile;
  public static String compressorFile;
  public static String extractedFeaturesFile;

  private static void makeDir(String path) {
    File outDir = new File(path);
    if (!outDir.exists()) {
        outDir.mkdir();
    }
  }

  public static void setTrainingPath(Properties props) {
    trainingPath = StatisticalCorefProperties.trainingPath(props);
    pairwiseModelsPath = trainingPath + "pairwise_models/";
    clusteringModelsPath = trainingPath + "clustering_models/";
    makeDir(pairwiseModelsPath);
    makeDir(clusteringModelsPath);
  }

  public static void setDataPath(String name) {
    String dataPath = trainingPath + name + "/";
    String extractedFeaturesPath = dataPath + EXTRACTED_FEATURES_NAME + "/";
    makeDir(dataPath);
    makeDir(extractedFeaturesPath);

    datasetFile = dataPath + "dataset.ser";
    predictionsName = name + "_predictions";
    goldClustersFile = dataPath + "gold_clusters.ser";
    mentionTypesFile = dataPath + "mention_types.ser";
    compressorFile = extractedFeaturesPath + "compressor.ser";
    extractedFeaturesFile = extractedFeaturesPath + "compressed_features.ser";
  }

  private static void preprocess(Properties props, Dictionaries dictionaries, boolean isTrainSet)
      throws Exception {
    (isTrainSet ? new DatasetBuilder(StatisticalCorefProperties.minClassImbalance(props),
        StatisticalCorefProperties.minTrainExamplesPerDocument(props)) :
          new DatasetBuilder()).runFromScratch(props, dictionaries);
    new MetadataWriter(isTrainSet).runFromScratch(props, dictionaries);
    new FeatureExtractorRunner(props, dictionaries).runFromScratch(props, dictionaries);
  }

  public static void doTraining(Properties props) throws Exception {
    props = StatisticalCorefProperties.addHcorefProps(props);
    setTrainingPath(props);
    Dictionaries dictionaries = new Dictionaries(props);

    setDataPath("train");
    wordCountsFile = "train/word_counts.ser";
    StatisticalCorefProperties.setInput(props, Dataset.TRAIN);
    preprocess(props, dictionaries, true);

    setDataPath("dev");
    StatisticalCorefProperties.setInput(props, Dataset.DEV);
    preprocess(props, dictionaries, false);

    setDataPath("train");
    dictionaries = null;
    PairwiseModel classificationModel = PairwiseModel.newBuilder(CLASSIFICATION_MODEL,
        MetaFeatureExtractor.newBuilder().build()).build();
    PairwiseModel rankingModel = PairwiseModel.newBuilder(RANKING_MODEL,
        MetaFeatureExtractor.newBuilder().build()).build();
    PairwiseModel anaphoricityModel = PairwiseModel.newBuilder(ANAPHORICITY_MODEL,
        MetaFeatureExtractor.anaphoricityMFE()).trainingExamples(5000000).build();
    PairwiseModelTrainer.trainRanking(rankingModel);
    PairwiseModelTrainer.trainClassification(classificationModel, false);
    PairwiseModelTrainer.trainClassification(anaphoricityModel, true);

    setDataPath("dev");
    PairwiseModelTrainer.test(classificationModel, predictionsName, false);
    PairwiseModelTrainer.test(rankingModel, predictionsName, false);
    PairwiseModelTrainer.test(anaphoricityModel, predictionsName, true);

    new Clusterer().doTraining(CLUSTERING_MODEL_NAME);
  }

  public static void main(String[] args) throws Exception {
    doTraining(StringUtils.argsToProperties(new String[] {"-props", args[0]}));
  }
}
