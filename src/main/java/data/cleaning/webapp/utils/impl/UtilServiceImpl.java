package data.cleaning.webapp.utils.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import data.cleaning.core.service.repair.impl.Candidate;
import data.cleaning.core.utils.objectives.Objective;
import data.cleaning.webapp.utils.UtilService;

public class UtilServiceImpl implements UtilService{

	public UtilServiceImpl() {}

	public List<Candidate> getSortedCandidates(Set<Candidate> candidatesSet,
			List<Objective> weightedFns) {
		List<Candidate> candidatesList = new ArrayList<Candidate>();
		
		for (Candidate c: candidatesSet) {
			candidatesList.add(c);
		}
		
		List<CandidateWithScore> candidatesWithScore = new ArrayList<CandidateWithScore>();
		
		for (Candidate can: candidatesList) {
			double overallScore = 0.0;
			for (Objective weightedFn : weightedFns) {
				double temp;
				if (weightedFn.getClass().getSimpleName()
						.equals("PrivacyObjective")) {
					temp = can.getPvtOut() * weightedFn.getWeight();
					
				} else if (weightedFn.getClass().getSimpleName()
						.equals("CleaningObjective")) {
					temp = can.getIndOut() * weightedFn.getWeight();
				} else {
					temp = can.getChangesOut() * weightedFn.getWeight();
				}
				overallScore += temp;
			}
			CandidateWithScore candidateWithScore = new CandidateWithScore();
			candidateWithScore.setCandidate(can);
			candidateWithScore.setOverallScore(overallScore);
			candidatesWithScore.add(candidateWithScore);
		}
		
		Collections.sort(candidatesWithScore, new Comparator<CandidateWithScore>(){
			public int compare(CandidateWithScore c1, CandidateWithScore c2) {
		        if (c1.getOverallScore() > c2.getOverallScore()) {
		        	return 1;
		        }
		        else {
		        	return 0;
		        }
		    }
		});
		
		List<Candidate> candidatesResult = new ArrayList<Candidate>();
		for (CandidateWithScore cTemp: candidatesWithScore) {
			candidatesResult.add(cTemp.getCandidate());
		}
		
		return candidatesResult;
	}
	
	// this class is for sorting Candidates by overallScore
		private class CandidateWithScore {
			private Candidate candidate;
			private double overallScore;
			
			public Candidate getCandidate() {
				return candidate;
			}
			public void setCandidate(Candidate candidate) {
				this.candidate = candidate;
			}
			public double getOverallScore() {
				return overallScore;
			}
			public void setOverallScore(double overallScore) {
				this.overallScore = overallScore;
			}
		}

}
