package com.water.dosing.model;

import java.util.ArrayList;
import java.util.List;

public class PolynomialRegressionModel {

    private double[] coefficients;
    private double intercept;
    private double[] featureMeans;
    private double[] featureStds;
    private double rSquared;
    private double mae;
    private boolean trained = false;

    public void train(List<double[]> features, List<Double> targets) {
        if (features.size() < 3) return;

        int n = features.size();
        int featureDim = features.get(0).length;
        int polyDim = featureDim * 3 + featureDim * (featureDim - 1) / 2;

        featureMeans = new double[featureDim];
        featureStds = new double[featureDim];

        for (int j = 0; j < featureDim; j++) {
            double sum = 0;
            for (double[] f : features) sum += f[j];
            featureMeans[j] = sum / n;
            double varSum = 0;
            for (double[] f : features) varSum += (f[j] - featureMeans[j]) * (f[j] - featureMeans[j]);
            featureStds[j] = Math.sqrt(varSum / n);
            if (featureStds[j] < 1e-10) featureStds[j] = 1.0;
        }

        double[][] X = new double[n][polyDim + 1];
        for (int i = 0; i < n; i++) {
            double[] normalized = new double[featureDim];
            for (int j = 0; j < featureDim; j++) {
                normalized[j] = (features.get(i)[j] - featureMeans[j]) / featureStds[j];
            }
            int col = 0;
            for (int j = 0; j < featureDim; j++) {
                X[i][col++] = normalized[j];
                X[i][col++] = normalized[j] * normalized[j];
                X[i][col++] = normalized[j] * normalized[j] * normalized[j];
            }
            for (int j = 0; j < featureDim; j++) {
                for (int k = j + 1; k < featureDim; k++) {
                    X[i][col++] = normalized[j] * normalized[k];
                }
            }
            X[i][col] = 1.0;
        }

        double[][] XtX = new double[polyDim + 1][polyDim + 1];
        double[] XtY = new double[polyDim + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= polyDim; j++) {
                for (int k = 0; k <= polyDim; k++) {
                    XtX[j][k] += X[i][j] * X[i][k];
                }
                XtY[j] += X[i][j] * targets.get(i);
            }
        }

        double ridge = 0.01;
        for (int i = 0; i <= polyDim; i++) XtX[i][i] += ridge;

        double[] beta = solveLinearSystem(XtX, XtY);

        intercept = beta[polyDim];
        coefficients = new double[polyDim];
        System.arraycopy(beta, 0, coefficients, 0, polyDim);

        double ssTotal = 0, ssResidual = 0, sumAbsErr = 0;
        double meanY = targets.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        for (int i = 0; i < n; i++) {
            double pred = predictInternal(features.get(i));
            double err = targets.get(i) - pred;
            ssResidual += err * err;
            ssTotal += (targets.get(i) - meanY) * (targets.get(i) - meanY);
            sumAbsErr += Math.abs(err);
        }
        rSquared = ssTotal > 0 ? 1 - ssResidual / ssTotal : 0;
        mae = sumAbsErr / n;
        trained = true;
    }

    private double predictInternal(double[] rawFeatures) {
        int featureDim = rawFeatures.length;
        int polyDim = featureDim * 3 + featureDim * (featureDim - 1) / 2;

        double[] normalized = new double[featureDim];
        for (int j = 0; j < featureDim; j++) {
            normalized[j] = (rawFeatures[j] - featureMeans[j]) / featureStds[j];
        }

        double result = intercept;
        int col = 0;
        for (int j = 0; j < featureDim; j++) {
            result += coefficients[col++] * normalized[j];
            result += coefficients[col++] * normalized[j] * normalized[j];
            result += coefficients[col++] * normalized[j] * normalized[j] * normalized[j];
        }
        for (int j = 0; j < featureDim; j++) {
            for (int k = j + 1; k < featureDim; k++) {
                result += coefficients[col++] * normalized[j] * normalized[k];
            }
        }
        return result;
    }

    public double predict(double turbidity, double flowRate) {
        if (!trained) return 0;
        double result = predictInternal(new double[]{turbidity, flowRate});
        return Math.max(0, result);
    }

    private double[] solveLinearSystem(double[][] A, double[] b) {
        int n = b.length;
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col])) maxRow = row;
            }
            double[] temp = aug[col];
            aug[col] = aug[maxRow];
            aug[maxRow] = temp;
            if (Math.abs(aug[col][col]) < 1e-12) continue;
            for (int row = col + 1; row < n; row++) {
                double factor = aug[row][col] / aug[col][col];
                for (int j = col; j <= n; j++) {
                    aug[row][j] -= factor * aug[col][j];
                }
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = aug[i][n];
            for (int j = i + 1; j < n; j++) {
                sum -= aug[i][j] * x[j];
            }
            x[i] = Math.abs(aug[i][i]) > 1e-12 ? sum / aug[i][i] : 0;
        }
        return x;
    }

    public boolean isTrained() { return trained; }
    public double[] getCoefficients() { return coefficients; }
    public double getIntercept() { return intercept; }
    public double[] getFeatureMeans() { return featureMeans; }
    public double[] getFeatureStds() { return featureStds; }
    public double getRSquared() { return rSquared; }
    public double getMae() { return mae; }

    public String serializeCoefficients() {
        if (coefficients == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < coefficients.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(coefficients[i]);
        }
        return sb.toString();
    }

    public String serializeArray(double[] arr) {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.toString();
    }
}
