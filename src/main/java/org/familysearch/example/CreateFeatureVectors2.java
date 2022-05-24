package org.familysearch.example;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.SQLOutput;
import java.util.*;

public class CreateFeatureVectors2 {

  private static final int[] DATE_FIELDS = {10, 24, 38, 66};

  private static final int[] LOCATION_FIELDS = {16, 18, 20, 30, 32, 34, 44, 46, 48, 72, 74, 76};
  private static final String OUTPUT_PATH = "data/javaVector";
  private static final String VECTOR_FILE = "data/javaVector";
  private static final String LIBSVM_TRAIN_FILE = "data/javaVector_train.libsvm";
  private static final String LIBSVM_EVAL_FILE = "data/javaVector_eval.libsvm";

  private static final int NUM_EVAL_LINES = 400;

  public static void main(String[] args) throws IOException {
    new CreateFeatureVectors2().run();
  }

  public void run() throws IOException {
    int foundCount = 0;
    List<Integer> dateFields = Utils.intArrayToList(DATE_FIELDS);
    List<Integer> locationFields = Utils.intArrayToList(LOCATION_FIELDS);
    List<String> lines = Utils.readLines("data/pairs.csv");
    Files.deleteIfExists(Paths.get(OUTPUT_PATH));
    for (String line : lines) {
      List<String> vectorValues = new ArrayList<>();
      List<String> fields = new ArrayList<>(Arrays.asList(line.split(",")));
      // Add the match value to the result vector
      vectorValues.add(fields.get(0));
      // Remove the first value that identifies the pair as a match or not
      fields.remove(0);
      for (int i = 0; i < fields.size() - 1; i = i + 2) {
        String targetField = fields.get(i);
        String candidateField = fields.get(i + 1);
        if (targetField.isEmpty() || candidateField.isEmpty()) {
          if (i == 10 && !targetField.isEmpty()) {
            candidateField = fields.get(39);
            if (!candidateField.isEmpty()) {
              int dateDifference = Math.abs(Integer.parseInt(targetField) - Integer.parseInt(candidateField));
              vectorValues.add(String.valueOf(15 < dateDifference && dateDifference < 50 ? 1 : 0));
              foundCount++;
            } else {
              candidateField = fields.get(25);
              if (!candidateField.isEmpty()) {
                int dateDifference = Math.abs(Integer.parseInt(targetField) - Integer.parseInt(candidateField));
                vectorValues.add(String.valueOf(dateDifference < 20 ? 1 : 0));
                foundCount++;
              }
            }
          } else if (i == 10 && !candidateField.isEmpty()) {
            targetField = fields.get(38);
            if (!targetField.isEmpty()) {
              int dateDifference = Math.abs(Integer.parseInt(targetField) - Integer.parseInt(candidateField));
              vectorValues.add(String.valueOf(15 < dateDifference && dateDifference < 50 ? 1 : 0));
              foundCount++;
            } else {
              targetField = fields.get(24);
              if (!targetField.isEmpty()) {
                int dateDifference = Math.abs(Integer.parseInt(targetField) - Integer.parseInt(candidateField));
                vectorValues.add(String.valueOf(dateDifference < 20 ? 1 : 0));
                foundCount++;
              }
            }
          }
          else {
            vectorValues.add("0");
          }
        }
        else if (i < 9) {
          int sameNames = 0;
          int differentNames = 0;
//          int sameChar = 0;
//          int differentChar = 0;
          List<String> targetNames = Arrays.asList(targetField.split(" "));
          List<String> candidateNames = Arrays.asList(candidateField.split(" "));
          for (String targetName : targetNames) {
            for (String candidateName : candidateNames) {
              if (targetName.equals(candidateName)) {
                sameNames++;
              }
              else {
                differentNames--;
              }

              // Character by Character comparison
//              for (char targetChar : targetName.toLowerCase().toCharArray()) {
//                for (char candidateChar : candidateName.toLowerCase().toCharArray()) {
//                  if (targetChar == candidateChar) {
//                    sameChar++;
//                  } else {
//                    differentChar--;
//                  }
//                }
//              }
            }
          }
          vectorValues.add(String.valueOf(sameNames > 0 ? sameNames : differentNames));
          //vectorValues.add(String.valueOf(sameChar > 0 ? sameChar : differentChar));
        }
        else if (dateFields.contains(i)) {
          try {
            int dateDifference = Math.abs(Integer.parseInt(targetField) - Integer.parseInt(candidateField));
            vectorValues.add(String.valueOf(dateDifference < 5 ? 5 - dateDifference : 0));
          }
          catch (NumberFormatException e) {
            vectorValues.add("0");
          }
        }
        else if (locationFields.contains(i)) {
          try {
            int dateDifference = Math.abs(Integer.parseInt(targetField) - Integer.parseInt(candidateField));
            vectorValues.add(String.valueOf(dateDifference == 0 ? 1 : -1));
          }
          catch (NumberFormatException e) {
            vectorValues.add("0");
          }
        }
        else {
          // Basic logic - check if the values are an exact match
          if (targetField.equals(candidateField)) {
            vectorValues.add("1");
          }
          else {
            vectorValues.add("0");
          }
        }
      }
      Path path = Paths.get(OUTPUT_PATH);
      try {
        Files.write(path,
          Collections.singletonList(vectorValues.toString().replaceAll("([,\\[\\]])", "")),
          StandardCharsets.UTF_8,
          Files.exists(path) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
      }
      catch (IOException e) {
        System.err.println("Unable to write to file " + OUTPUT_PATH);
        e.printStackTrace();
      }
    }
    System.out.println(foundCount);
    createLibSvmFile();
  }


  private void createLibSvmFile() {
    int linesCounted = 0;
    List<String> trainLines = new ArrayList<>();
    List<String> evalLines = new ArrayList<>();
    List<String> inputFile = Utils.readLines(VECTOR_FILE);
    for (String line : inputFile) {
      if (linesCounted < NUM_EVAL_LINES) {
        linesCounted++;
        evalLines.add(line);
      }
      else {
        trainLines.add(line);
      }
    }
    try {
      Files.deleteIfExists(Paths.get(LIBSVM_TRAIN_FILE));
      Files.deleteIfExists(Paths.get(LIBSVM_EVAL_FILE));
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    writeLibSvmLines(trainLines, LIBSVM_TRAIN_FILE);
    writeLibSvmLines(evalLines, LIBSVM_EVAL_FILE);
  }

  private void writeLibSvmLines(List<String> inputFile, String libsvmEvalFile) {
    Path libSvmEvalFile = Paths.get(libsvmEvalFile);
    try (BufferedWriter writer = Files.newBufferedWriter(libSvmEvalFile)) {
      StringJoiner sj = new StringJoiner(" ");
      for (String line : inputFile) {
        String[] features = line.split(" ");
        sj.add(features[0]);
        for (int i = 1; i < features.length; i++) {
          String featureScore = features[i];
          if (!"0".equals(featureScore)) {
            sj.add(i + ":" + featureScore);
          }
        }
        sj.add("\n");
      }
      writer.write(sj.toString());
    }
    catch (IOException e) {
      System.err.println("Unable to write to file " + libsvmEvalFile);
      e.printStackTrace();
    }
  }
}

