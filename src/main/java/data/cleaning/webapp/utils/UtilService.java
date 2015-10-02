package data.cleaning.webapp.utils;

import java.util.List;
import java.util.Set;

import data.cleaning.core.service.repair.impl.Candidate;
import data.cleaning.core.utils.objectives.Objective;

public interface UtilService {
	
	List<Candidate> getSortedCandidates (Set<Candidate> candidatesSet,
			List<Objective> weightedFns);
}
