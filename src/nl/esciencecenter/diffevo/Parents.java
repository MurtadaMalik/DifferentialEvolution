/*
 * Copyrighted 2012-2013 Netherlands eScience Center.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").  
 * You may not use this file except in compliance with the License. 
 * For details, see the LICENCE.txt file location in the root directory of this 
 * distribution or obtain the Apache License at the following location: 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 * 
 * For the full license, see: LICENCE.txt (located in the root folder of this distribution). 
 * ---
 */

package nl.esciencecenter.diffevo;

import java.util.ArrayList;
import java.util.Random;

import nl.esciencecenter.diffevo.statespacemodels.Model;

public class Parents {

	private ArrayList<Sample> sampleList; 
	private int nPop;
	private ParSpace parSpace;
	private int nDims;
	
	// constructor
	public Parents(int nPop, ParSpace parSpace){
		
		this.nDims = parSpace.getNumberOfPars();
		this.sampleList = new ArrayList<Sample>();
		this.nPop = nPop;
		this.parSpace = parSpace;
		for (int iPop = 1; iPop <= nPop; iPop++) {
			Sample sample = new Sample(nDims);
			sampleList.add(sample);
		}
	}
	
	public void add(Sample sample){
		sampleList.add(sample);
	}
	
	public void takeUniformRandomSamples(Random generator){

		for (int iPop=1;iPop<=nPop;iPop++){
			double[] values = new double[nDims];			
			for (int iDim=1;iDim<=nDims;iDim++){
				double g = generator.nextDouble();
				values[iDim-1] = parSpace.getLowerBound(iDim-1) + 
						g * parSpace.getRange(iDim-1);
			}
			this.setParameterVector(iPop-1, values);
		}
	}
	
	public ArrayList<Sample> getParents() {
		return this.sampleList;
	}
	
	public Sample getParent(int index) {
		return this.sampleList.get(index);
	}

	public void setParent(int index, Sample sample) {
		int sampleIdentifier;
		double[] parameterVector;
		double objScore;
		
		sampleIdentifier = sample.getSampleCounter();
		parameterVector = sample.getParameterVector();
		objScore = sample.getObjScore();
		
		this.sampleList.get(index).setSampleCounter(sampleIdentifier);
		this.sampleList.get(index).setParameterVector(parameterVector);
		this.sampleList.get(index).setObjScore(objScore);
		
	}

	public void setParameterVector(int index, double[] parameterVector) {
		this.sampleList.get(index).setParameterVector(parameterVector);
	}

	public double[] getParameterVector(int index) {
		return this.sampleList.get(index).getParameterVector();
	}

	public void setObjScore(int index, double objScore) {
		this.sampleList.get(index).setObjScore(objScore);
	}

	public double getObjScore(int index) {
		return this.sampleList.get(index).getObjScore();
	}

	public double calcObjScore(double[][] obs, double[] initState, ForcingChunks forcingChunks, TimeChunks timeChunks, ModelFactory modelFactory) {


		int nChunks = timeChunks.getnChunks();
		int nStates = initState.length;
		int nTimes = timeChunks.getnTimes();
		
		for (int iPop=0;iPop<nPop;iPop++){
			double[] parameterVector = getParameterVector(iPop);
			double[] state = new double[nStates];
			for (int iState=0;iState<nStates;iState++){
				state[iState] = initState[iState];
			}
			double[][] sim = new double[nStates][nTimes];
			for (int iState=0;iState<nStates;iState++){
				sim[iState][0] = Double.NaN;
			}
			for (int iChunk=0;iChunk<nChunks;iChunk++){
				double[] times = timeChunks.getChunk(iChunk);
				double[] forcing = forcingChunks.getChunk(iChunk);
				int[] indices = timeChunks.getChunkIndices(iChunk);
				int nIndices = indices.length;
				double[][] simChunk = new double[nStates][nIndices];
				
				Model model = modelFactory.create(state, parameterVector, forcing, times);
				simChunk = model.evaluate();
				
				for (int iState=0;iState<nStates;iState++){
					for (int iIndex=1;iIndex<nIndices;iIndex++){
						sim[iState][indices[iIndex]] = simChunk[iState][iIndex];
					}
					state[iState] = simChunk[iState][nIndices-1];
				}
			}//iChunk
			
			LikelihoodFunction likelihoodFunction = new LikelihoodFunction();
			double objScore = likelihoodFunction.evaluate(obs, sim);
			setObjScore(iPop, objScore);
			
		} //iPop		
		double objScore = 0;
		return objScore;
	}
	
}


