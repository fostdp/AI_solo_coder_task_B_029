package com.water.dosing.optimizer;

import com.water.dosing.entity.WaterSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MultiObjectiveWaterOptimizer {

    private static final Logger log = LoggerFactory.getLogger(MultiObjectiveWaterOptimizer.class);

    @Value("${water.optimization.target-turbidity:20.0}")
    private double targetTurbidity;

    @Value("${water.optimization.target-ph-min:6.5}")
    private double targetPhMin;

    @Value("${water.optimization.target-ph-max:8.5}")
    private double targetPhMax;

    @Value("${water.optimization.cost-weight:0.6}")
    private double costWeight;

    @Value("${water.optimization.quality-weight:0.4}")
    private double qualityWeight;

    @Value("${water.optimization.population-size:50}")
    private int populationSize;

    @Value("${water.optimization.max-iterations:100}")
    private int maxIterations;

    @Value("${water.optimization.mutation-rate:0.1}")
    private double mutationRate;

    @Value("${water.optimization.switch-penalty-weight:100.0}")
    private double switchPenaltyWeight;

    @Value("${water.optimization.enforce-switch-delay:true}")
    private boolean enforceSwitchDelay;

    public void setSwitchPenaltyWeight(double switchPenaltyWeight) { this.switchPenaltyWeight = switchPenaltyWeight; }
    public void setEnforceSwitchDelay(boolean enforceSwitchDelay) { this.enforceSwitchDelay = enforceSwitchDelay; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    public void setPopulationSize(int populationSize) { this.populationSize = populationSize; }

    @PostConstruct
    public void init() {
        log.info("MultiObjectiveWaterOptimizer initialized: costWeight={}, qualityWeight={}, targetTurbidity={}",
                costWeight, qualityWeight, targetTurbidity);
    }

    public static class OptimizationResult {
        private final Map<String, Double> allocations;
        private final double totalCost;
        private final double mixedTurbidity;
        private final double mixedPh;
        private final double fitnessScore;
        private final boolean feasible;

        public OptimizationResult(Map<String, Double> allocations, double totalCost,
                                   double mixedTurbidity, double mixedPh,
                                   double fitnessScore, boolean feasible) {
            this.allocations = allocations;
            this.totalCost = totalCost;
            this.mixedTurbidity = mixedTurbidity;
            this.mixedPh = mixedPh;
            this.fitnessScore = fitnessScore;
            this.feasible = feasible;
        }

        public Map<String, Double> getAllocations() { return allocations; }
        public double getTotalCost() { return totalCost; }
        public double getMixedTurbidity() { return mixedTurbidity; }
        public double getMixedPh() { return mixedPh; }
        public double getFitnessScore() { return fitnessScore; }
        public boolean isFeasible() { return feasible; }
    }

    public OptimizationResult optimize(List<WaterSource> sources, double totalDemand) {
        return optimize(sources, totalDemand, null);
    }

    public OptimizationResult optimize(List<WaterSource> sources, double totalDemand,
                                        Map<String, Double> previousAllocations) {
        if (sources == null || sources.isEmpty()) {
            return createDefaultResult(totalDemand);
        }

        List<WaterSource> activeSources = sources.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .collect(Collectors.toList());

        if (activeSources.isEmpty()) {
            return createDefaultResult(totalDemand);
        }

        return geneticAlgorithmOptimize(activeSources, totalDemand, previousAllocations);
    }

    private OptimizationResult geneticAlgorithmOptimize(List<WaterSource> sources, double totalDemand) {
        return geneticAlgorithmOptimize(sources, totalDemand, null);
    }

    private OptimizationResult geneticAlgorithmOptimize(List<WaterSource> sources, double totalDemand,
                                                         Map<String, Double> previousAllocations) {
        int n = sources.size();
        Random random = new Random();

        double[] prevRatios = extractPreviousRatios(sources, previousAllocations);
        double[] maxChangeRates = extractMaxChangeRates(sources);

        List<double[]> population = initializePopulation(n, populationSize, random, prevRatios, maxChangeRates);

        double[] bestChromosome = null;
        double bestFitness = Double.NEGATIVE_INFINITY;
        OptimizationResult bestResult = null;

        for (int iter = 0; iter < maxIterations; iter++) {
            List<double[]> newPopulation = new ArrayList<>();
            double[] fitnessScores = new double[populationSize];

            for (int i = 0; i < populationSize; i++) {
                double[] chromosome = population.get(i);
                OptimizationResult result = evaluateChromosome(sources, chromosome, totalDemand,
                        prevRatios, maxChangeRates);
                fitnessScores[i] = result.getFitnessScore();

                if (fitnessScores[i] > bestFitness) {
                    bestFitness = fitnessScores[i];
                    bestChromosome = chromosome.clone();
                    bestResult = result;
                }
            }

            while (newPopulation.size() < populationSize) {
                double[] parent1 = selectParent(population, fitnessScores, random);
                double[] parent2 = selectParent(population, fitnessScores, random);
                double[] child = crossover(parent1, parent2, random);
                mutate(child, mutationRate, random);
                normalizeChromosome(child);
                newPopulation.add(child);
            }

            population = newPopulation;
        }

        if (bestResult != null && bestResult.isFeasible()) {
            log.info("Optimization complete: cost={}, turbidity={}, feasible={}",
                    String.format("%.2f", bestResult.getTotalCost()),
                    String.format("%.2f", bestResult.getMixedTurbidity()),
                    bestResult.isFeasible());
            return bestResult;
        }

        return createFallbackResult(sources, totalDemand);
    }

    private double[] extractPreviousRatios(List<WaterSource> sources, Map<String, Double> previousAllocations) {
        double[] prevRatios = new double[sources.size()];
        if (previousAllocations != null && !previousAllocations.isEmpty()) {
            for (int i = 0; i < sources.size(); i++) {
                String code = sources.get(i).getSourceCode();
                prevRatios[i] = previousAllocations.getOrDefault(code, 1.0 / sources.size());
            }
        } else {
            double defaultRatio = 1.0 / sources.size();
            for (int i = 0; i < sources.size(); i++) {
                prevRatios[i] = defaultRatio;
            }
        }
        return prevRatios;
    }

    private double[] extractMaxChangeRates(List<WaterSource> sources) {
        double[] rates = new double[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            Double rate = sources.get(i).getMaxChangeRatePerHour();
            rates[i] = rate != null ? rate : 0.3;
        }
        return rates;
    }

    private List<double[]> initializePopulation(int n, int size, Random random,
                                                 double[] prevRatios, double[] maxChangeRates) {
        List<double[]> population = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            double[] chromosome = new double[n];
            if (prevRatios != null && i < size * 0.5) {
                for (int j = 0; j < n; j++) {
                    double maxChange = maxChangeRates[j];
                    double delta = (random.nextDouble() - 0.5) * 2 * maxChange;
                    chromosome[j] = Math.max(0.01, Math.min(0.99, prevRatios[j] + delta));
                }
            } else {
                for (int j = 0; j < n; j++) {
                    chromosome[j] = random.nextDouble();
                }
            }
            normalizeChromosome(chromosome);
            population.add(chromosome);
        }
        return population;
    }

    private double[] selectParent(List<double[]> population, double[] fitness, Random random) {
        double totalFitness = 0;
        for (double f : fitness) {
            totalFitness += Math.max(0, f);
        }

        if (totalFitness <= 0) {
            return population.get(random.nextInt(population.size()));
        }

        double r = random.nextDouble() * totalFitness;
        double cumulative = 0;
        for (int i = 0; i < population.size(); i++) {
            cumulative += Math.max(0, fitness[i]);
            if (cumulative >= r) {
                return population.get(i);
            }
        }
        return population.get(population.size() - 1);
    }

    private double[] crossover(double[] parent1, double[] parent2, Random random) {
        int n = parent1.length;
        double[] child = new double[n];
        int point = random.nextInt(n);
        for (int i = 0; i < n; i++) {
            child[i] = i < point ? parent1[i] : parent2[i];
        }
        return child;
    }

    private void mutate(double[] chromosome, double rate, Random random) {
        for (int i = 0; i < chromosome.length; i++) {
            if (random.nextDouble() < rate) {
                chromosome[i] += random.nextGaussian() * 0.1;
                chromosome[i] = Math.max(0.01, Math.min(0.99, chromosome[i]));
            }
        }
    }

    private void normalizeChromosome(double[] chromosome) {
        double sum = 0;
        for (double v : chromosome) sum += v;
        if (sum > 0) {
            for (int i = 0; i < chromosome.length; i++) {
                chromosome[i] /= sum;
            }
        }
    }

    private OptimizationResult evaluateChromosome(List<WaterSource> sources, double[] ratios, double totalDemand) {
        return evaluateChromosome(sources, ratios, totalDemand, null, null);
    }

    private OptimizationResult evaluateChromosome(List<WaterSource> sources, double[] ratios, double totalDemand,
                                                   double[] prevRatios, double[] maxChangeRates) {
        Map<String, Double> allocations = new LinkedHashMap<>();
        double totalCost = 0;
        double weightedTurbidity = 0;
        double weightedPh = 0;
        double totalRatio = 0;
        boolean feasible = true;
        double penalty = 0;
        double totalSwitchPenalty = 0;

        for (int i = 0; i < sources.size(); i++) {
            WaterSource source = sources.get(i);
            double ratio = ratios[i];
            double volume = ratio * totalDemand;

            Double minRatio = source.getMinAllocationRatio();
            if (minRatio != null && ratio < minRatio && ratio > 0.001) {
                penalty += (minRatio - ratio) * 200;
                feasible = false;
            }

            Double maxRatio = source.getMaxAllocationRatio();
            if (maxRatio != null && ratio > maxRatio) {
                penalty += (ratio - maxRatio) * 200;
                feasible = false;
            }

            Double maxSupply = source.getMaxSupply();
            if (maxSupply != null && volume > maxSupply) {
                penalty += (volume - maxSupply) * 100;
                feasible = false;
            }

            if (enforceSwitchDelay && prevRatios != null && maxChangeRates != null) {
                double prevRatio = prevRatios[i];
                double maxChange = maxChangeRates[i];
                double change = Math.abs(ratio - prevRatio);
                if (change > maxChange) {
                    double excessChange = change - maxChange;
                    totalSwitchPenalty += excessChange * switchPenaltyWeight * 10;
                    feasible = false;
                }
            }

            allocations.put(source.getSourceCode(), ratio);

            if (source.getUnitCost() != null) {
                totalCost += volume * source.getUnitCost();
            }
            if (source.getTurbidity() != null) {
                weightedTurbidity += ratio * source.getTurbidity();
            }
            if (source.getPh() != null) {
                weightedPh += ratio * source.getPh();
            }
            totalRatio += ratio;
        }

        if (weightedTurbidity > targetTurbidity) {
            penalty += (weightedTurbidity - targetTurbidity) * 500;
            feasible = false;
        }

        if (weightedPh < targetPhMin || weightedPh > targetPhMax) {
            penalty += 1000;
            feasible = false;
        }

        double costScore = -totalCost / 1000;
        double qualityScore = -(weightedTurbidity / targetTurbidity) * 100;
        double fitness = costWeight * costScore + qualityWeight * qualityScore - penalty - totalSwitchPenalty;

        return new OptimizationResult(allocations, totalCost, weightedTurbidity, weightedPh, fitness, feasible);
    }

    private OptimizationResult createDefaultResult(double totalDemand) {
        Map<String, Double> allocations = new LinkedHashMap<>();
        allocations.put("default", 1.0);
        return new OptimizationResult(allocations, 0, 15.0, 7.1, 0, true);
    }

    private OptimizationResult createFallbackResult(List<WaterSource> sources, double totalDemand) {
        Map<String, Double> allocations = new LinkedHashMap<>();
        double n = sources.size();
        double totalCost = 0;
        double weightedTurb = 0;
        double weightedPh = 0;

        for (WaterSource source : sources) {
            double ratio = 1.0 / n;
            allocations.put(source.getSourceCode(), ratio);
            if (source.getUnitCost() != null) {
                totalCost += ratio * totalDemand * source.getUnitCost();
            }
            if (source.getTurbidity() != null) {
                weightedTurb += ratio * source.getTurbidity();
            }
            if (source.getPh() != null) {
                weightedPh += ratio * source.getPh();
            }
        }

        return new OptimizationResult(allocations, totalCost, weightedTurb, weightedPh, 0, true);
    }
}
