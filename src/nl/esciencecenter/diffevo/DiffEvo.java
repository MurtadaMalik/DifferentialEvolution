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



import java.util.Random;

import nl.esciencecenter.diffevo.likelihoodfunctionfactories.LikelihoodFunctionFactory;
import nl.esciencecenter.diffevo.likelihoodfunctions.LikelihoodFunction;
import nl.esciencecenter.diffevo.statespacemodelfactories.ModelFactory;
import nl.esciencecenter.diffevo.statespacemodels.Model;

/**
 * This is the Differential Evolution algorithm by Storn and Price
 * 
 * @author Jurriaan H. Spaaks
 * 
 */
public class DiffEvo {

	private final int nPars;
	private final int nPop;
	private final int nGens;
	private ListOfParameterCombinations parents;
	private ListOfParameterCombinations  proposals;
	private EvalResults evalResults;
	private Random generator;
	private ParSpace parSpace;	
	private double[] initState;
	private ForcingChunks forcingChunks;
	private TimeChunks timeChunks;
	private double[][] obs;
	private ModelFactory modelFactory;
	private LikelihoodFunctionFactory likelihoodFunctionFactory;
	private final static long defaultRandomSeed = 0; 
	
	
	// constructor:
	DiffEvo(int nGens, int nPop, ParSpace parSpace, LikelihoodFunctionFactory likelihoodFunctionFactory) {
		// use seed of zero by default:
		this(nGens, nPop, parSpace, likelihoodFunctionFactory, defaultRandomSeed);
	}

	// constructor:
	DiffEvo(int nGens, int nPop, ParSpace parSpace, StateSpace stateSpace, double[] initState, double[] forcing, double[] times, 
			double[] assimilate, double[][] obs, ModelFactory modelFactory, LikelihoodFunctionFactory likelihoodFunctionFactory) {
		// use seed of zero by default:
		this(nGens, nPop, parSpace, stateSpace, initState, forcing, times, assimilate, obs, 
				modelFactory, likelihoodFunctionFactory, defaultRandomSeed);
	}
	
	// constructor:
	DiffEvo(int nGens, int nPop, ParSpace parSpace, LikelihoodFunctionFactory likelihoodFunctionFactory, long seed) {
		this.nGens = nGens;
		this.nPop = nPop;
		this.nPars = parSpace.getNumberOfPars();
		this.parSpace = parSpace;
		this.parents = new ListOfParameterCombinations(nPop, nPars);
		this.proposals = new ListOfParameterCombinations(nPop, nPars);
		this.generator = new Random();
		this.generator.setSeed(seed);
		this.likelihoodFunctionFactory = likelihoodFunctionFactory;
		this.evalResults = new EvalResults(nGens, nPop, parSpace, likelihoodFunctionFactory, generator);
	}

	// constructor:
	DiffEvo(int nGens, int nPop, ParSpace parSpace, StateSpace stateSpace, double[] initState, double[] forcing, double[] times, 
			double[] assimilate, double[][] obs, ModelFactory modelFactory, LikelihoodFunctionFactory likelihoodFunctionFactory, long seed) {
		this.nGens = nGens;
		this.nPop = nPop;
		this.parSpace = parSpace;
		if (modelFactory!=null){
			this.initState = initState.clone();
			this.forcingChunks = new ForcingChunks(forcing.clone(), assimilate.clone());
			this.timeChunks = new TimeChunks(times.clone(), assimilate.clone());
		}
		this.nPars = parSpace.getNumberOfPars();
		this.parents = new ListOfParameterCombinations(nPop, nPars);
		this.proposals = new ListOfParameterCombinations(nPop, nPars);
		this.generator = new Random();
		this.generator.setSeed(seed);
		this.obs = obs.clone();
		this.modelFactory = modelFactory;
		this.likelihoodFunctionFactory = likelihoodFunctionFactory;
		this.evalResults = new EvalResults(nGens, nPop, parSpace, stateSpace, initState, forcing, times, 
				assimilate, obs, modelFactory, likelihoodFunctionFactory, generator);
	}

	
	public EvalResults runOptimization(){
		System.out.println("Starting Differential Evolution optimization...");		
		initializeParents();
		for (int iGen = 1;iGen<nGens;iGen++){
			proposeOffSpring();
			updateParentsWithProposals();
		}
		return evalResults; 
	}
	
	
	public void initializeParents(){
		
		for (int iPop=0;iPop<nPop;iPop++){
			double[] parameterCombination = parSpace.takeUniformRandomSample(generator);
			parents.setParameterCombination(iPop, parameterCombination);
		}
		parents = calcObjScores(parents, obs, initState,forcingChunks,timeChunks,modelFactory, likelihoodFunctionFactory);
		
		// now add the initial values of parents to the record, i.e. evalResults
		for (int iPop=0;iPop<nPop;iPop++){
			int sampleIdentifier = iPop; 
			double[] parameterCombination = parents.getParameterCombination(iPop);
			double objScore = parents.getObjScore(iPop);
			EvalResult evalResult = new EvalResult(sampleIdentifier, parameterCombination, objScore);
			evalResults.add(evalResult);
		}
	}
	
	public ListOfParameterCombinations calcObjScores(ListOfParameterCombinations parameterCombinations, double[][] obs, double[] initState, ForcingChunks forcingChunks,
			TimeChunks timeChunks, ModelFactory modelFactory, LikelihoodFunctionFactory likelihoodFunctionFactory) {

		LikelihoodFunction likelihoodFunction = likelihoodFunctionFactory.create();
		
		if (modelFactory!=null){
			int nChunks = timeChunks.getnChunks();

			int nStates = initState.length;
			int nTimes = timeChunks.getnTimes();

			for (int iPop=0;iPop<nPop;iPop++){
				double[] parameterVector = parameterCombinations.getParameterCombination(iPop);
				double[] state = new double[initState.length];
				System.arraycopy(initState, 0, state, 0, nStates);
				
				double[][] sim = new double[nStates][nTimes];
				for (int iState=0;iState<nStates;iState++){
					sim[iState][0] = Double.NaN;
				}
				for (int iChunk=0;iChunk<nChunks;iChunk++){
					double[] times = timeChunks.getChunk(iChunk);
					double[] forcing = forcingChunks.getChunk(iChunk);
					int[] indices = timeChunks.getChunkIndices(iChunk);
					int nIndices = indices.length;

					Model model = modelFactory.create(state, parameterVector, forcing, times);
					double[][] simChunk = model.evaluate();

					for (int iState=0;iState<nStates;iState++){
						for (int iIndex=1;iIndex<nIndices;iIndex++){
							sim[iState][indices[iIndex]] = simChunk[iState][iIndex];
						}
						state[iState] = simChunk[iState][nIndices-1];
					}
				}//iChunk
				
				double objScore = likelihoodFunction.evaluate(obs, sim);
				parameterCombinations.setObjScore(iPop, objScore);

			} //iPop		
		}
		else {
			for (int iPop=0;iPop<nPop;iPop++){
				double[] parameterVector = parameterCombinations.getParameterCombination(iPop);
				double objScore = likelihoodFunction.evaluate(parameterVector);
				parameterCombinations.setObjScore(iPop, objScore);
			} // iPop
		} //else
		
		return parameterCombinations;

	} // calcObjScore()

	
	
	
	public void proposeOffSpring(){
	
		final int nDraws = 3;
		final double diffEvoParF = 0.6;
		final double diffEvoParK = 0.4;
		int[] availables = new int[nDraws];
		boolean drawAgain = true;
		int index;
		double[] dist1;
		double[] dist2;
		double[] proposal;
		double[] parent;
		
		// draw 3 random integer indices from [0,nPop], but not your own index and no recurrent samples
		for (int iPop=0;iPop<nPop;iPop++){
			availables[0] = -1;
			availables[1] = -1;
			availables[2] = -1;
			for (int iDraw=0;iDraw<nDraws;iDraw++){
				drawAgain = true;
				index = -1;
				while (drawAgain){
					index = generator.nextInt(nPop);
					drawAgain = index == iPop | index == availables[0] | index == availables[1] | index == availables[2];
				}
				availables[iDraw] = index;
			}
			
			dist1 = calcDistance(iPop, availables, 1);
			dist2 = calcDistance(iPop, availables, 2);
			
			parent = parents.getParameterCombination(iPop);

			proposal = new double[nPars];
			for (int iPar=0;iPar<nPars;iPar++){
				proposal[iPar] = parent[iPar] + diffEvoParF * dist1[iPar] + diffEvoParK * dist2[iPar]; 				
			}
			proposals.setParameterCombination(iPop, proposal);
 		}
		proposals = parSpace.reflectIfOutOfBounds(proposals);
		proposals = calcObjScores(proposals, obs, initState, forcingChunks, timeChunks, modelFactory, likelihoodFunctionFactory);
	}
	
	private double[] calcDistance(int iPop, int[] availables, int distanceOneOrTwo){
		int fromIndex;
		int toIndex;
		double[] fromPoint;
		double[] toPoint;
		double[] dist;
 		
		if (distanceOneOrTwo==1){
			fromIndex = iPop;
			toIndex = availables[0];
		}
		else{
			fromIndex = availables[1];
			toIndex = availables[2];
		}
		
		fromPoint = parents.getParameterCombination(fromIndex);
		toPoint = parents.getParameterCombination(toIndex);
		
		dist = new double[nPars];

		for (int iPar=0;iPar<nPars;iPar++){
			dist[iPar] = fromPoint[iPar]-toPoint[iPar];
		}
		
		return dist;
	}

	public void updateParentsWithProposals(){
		double scoreParent;
		double scoreProposal;
		int nModelEvals = evalResults.getNumberOfEvalResults();

		double logOfUnifRandDraw;
		double[] parameterCombination;
		double objScore;
		
		for (int iPop=0;iPop<nPop;iPop++){
			scoreParent = parents.getObjScore(iPop);
			scoreProposal = proposals.getObjScore(iPop);
			logOfUnifRandDraw = Math.log(generator.nextDouble());
			int sampleIdentifier = nModelEvals+iPop;
			if (scoreProposal-scoreParent >= logOfUnifRandDraw){
				// accept proposal
				parameterCombination = proposals.getParameterCombination(iPop);
				objScore = proposals.getObjScore(iPop);
			}
			else{
				// reject proposal
				parameterCombination = parents.getParameterCombination(iPop);
				objScore = parents.getObjScore(iPop);
			}
			
			parents.setParameterCombination(iPop, parameterCombination);
			parents.setObjScore(iPop, objScore);
			
			// add most recent sample to the record array evalResults:
			EvalResult evalResult = new EvalResult(sampleIdentifier, parameterCombination, objScore);
			evalResults.add(evalResult);
		}
	}
	
	public int getnPop() {
		return nPop;
	}

	
	
}
