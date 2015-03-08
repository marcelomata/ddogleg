/*
 * Copyright (c) 2012-2015, Peter Abeles. All Rights Reserved.
 *
 * This file is part of DDogleg (http://ddogleg.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ddogleg.clustering.kmeans;

import org.ddogleg.clustering.AssignCluster;
import org.ddogleg.clustering.ComputeClusters;
import org.ddogleg.struct.FastQueue;
import org.ddogleg.struct.GrowQueue_I32;

import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Standard implementation of k-means [1], summary is provided below:
 * <ol>
 * <li> The initial seeds for each cluster is selected by the provided
 * {@link InitializeKMeans_F64}.
 * <li> Each point is assigned to a cluster which minimizes the euclidean distance squared.
 * <li> New cluster centers are computed from the average of all points assigned to it.
 * </ol>
 * This will find a locally optimal solution which minimizes the sum of the distance-squared of each point
 * to the cluster they are assigned to.
 * </p>
 * <p>
 * [1] Lloyd, S. P. (1957). "Least square quantization in PCM". Bell Telephone Laboratories Paper.
 * Published in journal much later: Lloyd., S. P. (1982)
 * </p>
 *
 * @author Peter Abeles
 */
public class StandardKMeans_F64 implements ComputeClusters<double[]> {

	// number of elements in each point
	int N;

	// maximum number of iterations
	int maxIterations;

	// It is considered to be converged when the change in sum score is <= than this amount.
	double threshScoreChange;

	// selects the initial locations of each seed
	InitializeKMeans_F64 seedSelector;
	// Storage for the seeds
	FastQueue<double[]> clusters;

	// labels for all the points
	GrowQueue_I32 labels = new GrowQueue_I32();

	// work space for computing the new cluster centers.  The sum for points in a cluster is computed on the fly
	// instead of labeling each point and computing it later.  Should save memory and maybe slightly faster.
	FastQueue<double[]> workClusters;
	GrowQueue_I32 memberCount = new GrowQueue_I32();

	// reference to the assigning algorithm which is returned
	AssignKMeans_F64 assign;

	// distance of the best match to the point
	double bestDistance;
	// sum of distances for all the points
	double sumDistance;

	/**
	 * Configures k-means parameters
	 *
	 * @param maxIterations Maximum number of iterations
	 * @param threshScoreChange It is considered to be converged when the change in sum score is <= than this amount.
	 * @param seedSelector Used to select initial seeds for the clusters
	 */
	public StandardKMeans_F64(int maxIterations, double threshScoreChange,
							  InitializeKMeans_F64 seedSelector) {
		this.maxIterations = maxIterations;
		this.threshScoreChange = threshScoreChange;
		this.seedSelector = seedSelector;
	}

	@Override
	public void init(final int pointDimension, long randomSeed) {
		seedSelector.init(pointDimension,randomSeed);
		this.N = pointDimension;

		clusters = createQueue(pointDimension);
		workClusters = createQueue(pointDimension);
		memberCount.resize(pointDimension);

		assign = new AssignKMeans_F64(clusters.toList());
	}

	private FastQueue<double[]> createQueue( final int pointDimension ) {
		return new FastQueue<double[]>(double[].class,true) {
			@Override
			protected double[] createInstance() {
				return new double[pointDimension];
			}
		};
	}

	@Override
	public void process(List<double[]> points, int numCluster) {
		// declare data
		clusters.resize(numCluster);
		workClusters.resize(numCluster);
		memberCount.resize(numCluster);
		labels.resize(points.size());

		// select the initial seeds
		seedSelector.selectSeeds(points, clusters.toList());

		// run standard k-means
		double previousSum = Double.MAX_VALUE;
		for (int iteration = 0; iteration < maxIterations; iteration++) {
			// zero the work seeds.  These will be used
			for (int i = 0; i < workClusters.size(); i++) {
				Arrays.fill(workClusters.data[i],0);
			}
			memberCount.fill(0);

			matchPointsToClusters(points);

			updateClusterCenters();

			// check for convergence
			double fractionalChange = 1.0-sumDistance/previousSum;
			if( fractionalChange > 0 && fractionalChange <= threshScoreChange )
				break;

			previousSum = sumDistance;
		}
	}

	/**
	 * Finds the cluster which is the closest to each point.  The point is the added to the sum for the cluster
	 * and its member count incremented
	 */
	protected void matchPointsToClusters(List<double[]> points) {
		sumDistance = 0;
		for (int i = 0; i < points.size(); i++) {
			double[]p = points.get(i);

			// find the cluster which is closest to the point
			int bestCluster = findBestMatch(p);

			// sum up all the points which are members of this cluster
			double[] c = workClusters.get(bestCluster);
			for (int j = 0; j < c.length; j++) {
				c[j] += p[j];
			}
			memberCount.data[bestCluster]++;
			labels.data[i] = bestCluster;
			sumDistance += bestDistance;
		}
	}

	/**
	 * Searches for this cluster which is the closest to p
	 */
	protected int findBestMatch(double[] p) {
		int bestCluster = -1;
		bestDistance = Double.MAX_VALUE;

		for (int j = 0; j < clusters.size; j++) {
			double d = distanceSq(p,clusters.get(j));
			if( d < bestDistance ) {
				bestDistance = d;
				bestCluster = j;
			}
		}
		return bestCluster;
	}

	/**
	 * Sets the location of each cluster to the average location of all its members.
	 */
	protected void updateClusterCenters() {
		// compute the new centers of each cluster
		for (int i = 0; i < clusters.size; i++) {
			double mc = memberCount.get(i);
			double[] w = workClusters.get(i);
			double[] c = clusters.get(i);

			for (int j = 0; j < w.length; j++) {
				c[j] = w[j] / mc;
			}
		}
	}

	/**
	 * Returns the euclidean distance squared between the two poits
	 */
	protected static double distanceSq(double[] a, double[] b) {
		double sum = 0;
		for (int i = 0; i < a.length; i++) {
			double d = a[i]-b[i];
			sum += d*d;
		}
		return sum;
	}

	/**
	 * Returns the labels assigned to each point
	 */
	public GrowQueue_I32 getPointLabels() {
		return labels;
	}

	/**
	 * Returns the mean of each cluster
	 */
	public FastQueue<double[]> getClusterMeans() {
		return clusters;
	}

	@Override
	public AssignCluster<double[]> getAssignment() {
		return assign;
	}

	/**
	 * Computes the potential function.  The sum of distance for each point from their cluster centers.\
	 */
	@Override
	public double getDistanceMeasure() {
		return sumDistance;
	}
}
