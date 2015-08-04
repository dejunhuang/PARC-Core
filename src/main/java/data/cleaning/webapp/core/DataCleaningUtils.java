package data.cleaning.webapp.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

import data.cleaning.core.service.dataset.impl.Constraint;
import data.cleaning.core.service.dataset.impl.Dataset;
import data.cleaning.core.service.dataset.impl.MasterDataset;
import data.cleaning.core.service.dataset.impl.Record;
import data.cleaning.core.service.dataset.impl.TargetDataset;
import data.cleaning.core.service.matching.impl.Match;
import data.cleaning.core.service.repair.impl.Candidate;
import data.cleaning.core.service.repair.impl.Violations;
import data.cleaning.core.utils.search.Search;
import data.cleaning.core.utils.search.SearchType;

public interface DataCleaningUtils {
	
	String runDataCleaning (TargetDataset tgtDataset, MasterDataset mDataset, 
			float simThreshold, SearchType searchType, Map<String, Double> params);
	
	String runDataCleaning (TargetDataset tgtDataset, MasterDataset mDataset, 
			float simThreshold, SearchType searchType);
	
	Violations findVios (Dataset tgtDataset, Constraint constraint);
	
	List<Match> getMatching (Constraint constraint,
			List<Record> tgtRecords, List<Record> mRecords, float simThreshold,
			String tgtFileName, String mFileName);
	
	TargetDataset loadTargetDataset (String tgtUrl, String tgtFileName,
			String fdUrl, char separator, char quoteChar);
	
	MasterDataset loadMasterDataset (String mUrl, String mFileName,
			String fdUrl, long targetId, char separator, char quoteChar);
	
	Set<Candidate> getRecommendations (Constraint constraint,
			List<Match> tgtMatches, Search search, TargetDataset tgtDataset,
			MasterDataset mDataset, boolean shdReturnInit);
}