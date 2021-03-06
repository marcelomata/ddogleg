/*
 * Copyright (c) 2012-2013, Peter Abeles. All Rights Reserved.
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

package org.ddogleg.nn;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.ddogleg.nn.BenchmarkNearestNeighbor.createData;

/**
 * @author Peter Abeles
 */
public class BenchmarkNearestNeighborCorrect {

	int dimen;
	List<double[]> cloud;
	List<double[]> searchSet;
	double[] solutions[];
	double maxDistance;
	NnData result = new NnData();

	NearestNeighbor exhaustive = FactoryNearestNeighbor.exhaustive();

	private double computeCorrectness( NearestNeighbor alg ) {
		alg.init(dimen);
		alg.setPoints(cloud,null);

		int numCorrect = 0;
		for( int i = 0; i < searchSet.size(); i++ ) {
			double []p = searchSet.get(i);
			if( alg.findNearest(p,maxDistance,result) ) {
				if( solutions[i] == result.point )
					numCorrect++;
			}
		}

		return 100.0*numCorrect/searchSet.size();
	}


	public List<Subject> createAlg() {
		List<Subject> ret = new ArrayList<Subject>();

//		ret.add( new Subject(FactoryNearestNeighbor.exhaustive(),"Exhaustive"));
//		ret.add( new Subject(FactoryNearestNeighbor.kdtree(),"kdtree"));
		ret.add( new Subject(FactoryNearestNeighbor.kdtree(200),"kdtree P "));
		ret.add( new Subject(FactoryNearestNeighbor.kdRandomForest(200,20,5,23423432),"K-D Random Forest"));
		ret.add( new Subject(FactoryNearestNeighbor.kdtree(500),"kdtree P "));
		ret.add( new Subject(FactoryNearestNeighbor.kdtree(1000),"kdtree P "));
		ret.add( new Subject(FactoryNearestNeighbor.kdtree(2000),"kdtree P "));
//		ret.add( new Subject(FactoryNearestNeighbor.kdtree(5000),"kdtree P "));
//		ret.add( new Subject(FactoryNearestNeighbor.kdtree(10000),"kdtree P "));

		return ret;
	}

	public void evaluateDataSet( int dimen , int cloudSize , int searchSize ) {
		Random rand = new Random(234);

		this.dimen = dimen;
		this.cloud = createData(rand,cloudSize,dimen);
		this.searchSet = createData(rand,searchSize,dimen);
		this.solutions = new double[ searchSize ][];
		this.maxDistance = 10;

		System.out.println("Computing solutions");
		exhaustive.init(dimen);
		exhaustive.setPoints(cloud,null);
		for( int i = 0; i < searchSize; i++ ) {
			exhaustive.findNearest(searchSet.get(i),maxDistance,result);
			solutions[i] = result.point;
		}

		System.out.println("K = "+dimen+"  cloud = "+cloudSize);
		for( Subject alg : createAlg() ) {
			System.out.printf("%20s = %4.1f\n",alg.name,computeCorrectness(alg.alg));
		}
	}


	private static class Subject {
		public String name;
		public NearestNeighbor alg;

		private Subject(NearestNeighbor alg,String name) {
			this.name = name;
			this.alg = alg;
		}
	}


	// TODO have a search set
	// TODO Compute correct solution using exhaustive
	public static void main( String args[] ) {
		BenchmarkNearestNeighborCorrect app = new BenchmarkNearestNeighborCorrect();

//		app.evaluateDataSet(3,30);
//		app.evaluateDataSet(3,300);
//		app.evaluateDataSet(3,600);
//		app.evaluateDataSet(5,10000);
		app.evaluateDataSet(10,100000,1000);
		app.evaluateDataSet(20,100000,1000);
		app.evaluateDataSet(60,100000,1000);
//		app.evaluateDataSet(120,100000,1000);

		try {
			synchronized ( app ) {
				Thread.sleep(100);
			}
		} catch (InterruptedException e) {}
	}
}
