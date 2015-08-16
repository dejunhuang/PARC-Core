package data.cleaning.webapp.core.implementation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Multimap;

import data.cleaning.core.service.dataset.DatasetService;
import data.cleaning.core.service.dataset.impl.Constraint;
import data.cleaning.core.service.dataset.impl.Dataset;
import data.cleaning.core.service.dataset.impl.DatasetServiceImpl;
import data.cleaning.core.service.dataset.impl.InfoContentTable;
import data.cleaning.core.service.dataset.impl.MasterDataset;
import data.cleaning.core.service.dataset.impl.Record;
import data.cleaning.core.service.dataset.impl.TargetDataset;
import data.cleaning.core.service.matching.MatchingService;
import data.cleaning.core.service.matching.impl.Match;
import data.cleaning.core.service.matching.impl.MatchingServiceImpl;
import data.cleaning.core.service.repair.RepairService;
import data.cleaning.core.service.repair.impl.Candidate;
import data.cleaning.core.service.repair.impl.RepairServiceImpl;
import data.cleaning.core.service.repair.impl.Violations;
import data.cleaning.core.utils.Config;
import data.cleaning.core.utils.Pair;
import data.cleaning.core.utils.objectives.ChangesObjective;
import data.cleaning.core.utils.objectives.CustomCleaningObjective;
import data.cleaning.core.utils.objectives.Objective;
import data.cleaning.core.utils.objectives.PrivacyObjective;
import data.cleaning.core.utils.search.Search;
import data.cleaning.core.utils.search.SearchType;
import data.cleaning.core.utils.search.SimulAnnealEps;
import data.cleaning.core.utils.search.SimulAnnealEpsDynamic;
import data.cleaning.core.utils.search.SimulAnnealEpsFlexible;
import data.cleaning.core.utils.search.SimulAnnealEpsLex;
import data.cleaning.core.utils.search.SimulAnnealWeighted;
import data.cleaning.webapp.core.DataCleaningUtils;

public class DataCleaningUtilsImpl implements DataCleaningUtils{
	private DatasetService datasetService = new DatasetServiceImpl();
	private RepairService repairService = new RepairServiceImpl();
	private MatchingService matchingService = new MatchingServiceImpl();
	
	private Search initSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		double startTemp = Config.START_TEMP_EVP * (double) (1d);
		Pair<Objective, Set<Objective>> obj = constructEpsObjective(constraint, mDataset, 
				Config.EPSILON_CLEANING, Config.EPSILON_SIZE);
		Search search = new SimulAnnealEps(obj.getO1(), obj.getO2(), startTemp,
				Config.FINAL_TEMP_EVP, Config.ALPHA_TEMP_EVP,
				Config.BEST_ENERGY_EVP, Config.INIT_STRATEGY,
				Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Search initWeightedSimulAnneal(MasterDataset mDataset, Constraint constraint, double stTemp, double endTemp, double alpTemp, double bestEn, 
			double alphaPvt, double betaInd, double gamaSize) {
		double startTemp = stTemp * (double) (1d);
		List<Objective> objective = constructWeightedObjective(
				constraint, mDataset, alphaPvt, betaInd, gamaSize);
		Search search = new SimulAnnealWeighted(objective, startTemp, endTemp,
				alpTemp, bestEn,Config.INIT_STRATEGY, Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Search initWeightedSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		return initWeightedSimulAnneal(mDataset, constraint, 
				Config.START_TEMP_EVP, Config.FINAL_TEMP, Config.ALPHA_TEMP, Config.BEST_ENERGY, 
				Config.ALPHA_PVT, Config.BETA_IND, Config.GAMMA_SIZE);
	}
	
	private Search initFlexibleSimulAnneal(MasterDataset mDataset, Constraint constraint, double stTemp, double endTemp, double alpTemp, double bestEn,
			double cleaning, double size) {
		Pair<Objective, Set<Objective>> obj = constructEpsFlexibleObjectiveEVP(
				constraint, mDataset, cleaning, size);
		double startTemp = stTemp * (double) (1d);
		Search search = new SimulAnnealEpsFlexible(obj.getO1(), obj.getO2(),
				startTemp, endTemp, alpTemp,
				bestEn, Config.INIT_STRATEGY,
				Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Search initFlexibleSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		return initFlexibleSimulAnneal(mDataset, constraint, 
				Config.START_TEMP, Config.FINAL_TEMP, Config.ALPHA_TEMP, Config.BEST_ENERGY, 
				Config.EPSILON_FLEX_CLEANING, Config.EPSILON_FLEX_SIZE);
	}
	
	private Search initDynamicSimulAnneal(MasterDataset mDataset, Constraint constraint, double stTemp, double endTemp, double alpTemp, double bestEn,
			double cleaning, double size) {
		double startTemp = stTemp * (double) (1d);
		Pair<Objective, Set<Objective>> obj = constructEpsDynamicObjective(
				constraint, mDataset, cleaning, size);
		Search search = new SimulAnnealEpsDynamic(obj.getO1(), obj.getO2(),
				startTemp, endTemp, alpTemp,
				bestEn, Config.INIT_STRATEGY,
				Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Search initDynamicSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		return initDynamicSimulAnneal(mDataset, constraint, 
				Config.START_TEMP, Config.FINAL_TEMP, Config.ALPHA_TEMP, Config.BEST_ENERGY, 
				Config.EPSILON_PREV_CLEANING, Config.EPSILON_PREV_SIZE);
	}
	
	private Search initLexicalSimulAnneal(MasterDataset mDataset, Constraint constraint, double stTemp, double endTemp, double alpTemp, double bestEn,
			double privacy, double cleaning) {
		double startTemp = stTemp * (double) (1d);
		Search search = new SimulAnnealEpsLex(constructEpsLexObjective(constraint,
				mDataset, privacy, cleaning), startTemp, endTemp, alpTemp,
				bestEn, Config.INIT_STRATEGY,
				Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Search initLexicalSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		return initLexicalSimulAnneal(mDataset, constraint, 
				Config.START_TEMP, Config.FINAL_TEMP, Config.ALPHA_TEMP, Config.BEST_ENERGY, 
				Config.EPSILON_LEX_PVT, Config.EPSILON_LEX_CLEANING);
	}
	
	private Pair<Objective, Set<Objective>> constructEpsObjective(Constraint constraint, MasterDataset mDataset, double cleaning, double size) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);

		Objective pvtFn = new PrivacyObjective(0d, 0d, true, constraint, table);
		Objective cleanFn = new CustomCleaningObjective(
				cleaning, 0d, true, constraint, table);
		Objective changesFn = new ChangesObjective(size, 0d,
				true, constraint, table);
		Set<Objective> constraintFns = new HashSet<Objective>();
		constraintFns.add(cleanFn);
		constraintFns.add(changesFn);

		Pair<Objective, Set<Objective>> p = new Pair<Objective, Set<Objective>>();
		p.setO1(pvtFn);
		p.setO2(constraintFns);

		return p;
	}
	
	private Pair<Objective, Set<Objective>> constructEpsFlexibleObjectiveEVP(
			Constraint constraint, MasterDataset mDataset, double cleaning, double size) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);

		Objective pvtFn = new PrivacyObjective(0d, 0d, true, constraint, table);
		Objective cleanFn = new CustomCleaningObjective(
				cleaning, 0d, true, constraint, table);
		Objective changesFn = new ChangesObjective(size,
				0d, true, constraint, table);
		Set<Objective> constraintFns = new HashSet<Objective>();
		constraintFns.add(cleanFn);
		constraintFns.add(changesFn);

		Pair<Objective, Set<Objective>> p = new Pair<Objective, Set<Objective>>();
		p.setO1(pvtFn);
		p.setO2(constraintFns);

		return p;
	}
	
	private List<Objective> constructWeightedObjective(Constraint constraint,
			MasterDataset mDataset, double alphaPvt, double betaInd, double gamaSize) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);
		List<Objective> weightedFns = new ArrayList<Objective>();
		Objective pvtFn = new PrivacyObjective(0d, alphaPvt, true,
				constraint, table);
		Objective indFn = new CustomCleaningObjective(0d, betaInd,
				true, constraint, table);
		Objective changesFn = new ChangesObjective(0d, gamaSize, true,
				constraint, table);
		weightedFns.add(pvtFn);
		weightedFns.add(indFn);
		weightedFns.add(changesFn);

		return weightedFns;
	}
	
	private List<Objective> constructEpsLexObjective(Constraint constraint,MasterDataset mDataset,
			double privacy, double cleaning) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);
		Objective pvtFn = new PrivacyObjective(privacy, 0d,
				true, constraint, table);
		Objective cleanFn = new CustomCleaningObjective(
				cleaning, 0d, true, constraint, table);
		Objective changesFn = new ChangesObjective(0d, 0d, true, constraint,
				table);

		List<Objective> fns = new ArrayList<Objective>();
		fns.add(pvtFn);
		fns.add(cleanFn);
		fns.add(changesFn);

		return fns;
	}
	
	private Pair<Objective, Set<Objective>> constructEpsDynamicObjective(Constraint constraint, MasterDataset mDataset,
			double cleaning, double size) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);
		Objective pvtFn = new PrivacyObjective(0d, 0d, false, constraint, table);
		Objective cleanFn = new CustomCleaningObjective(
				cleaning, 0d, false, constraint, table);
		Objective changesFn = new ChangesObjective(size,
				0d, false, constraint, table);
		Set<Objective> constraintFns = new HashSet<Objective>();
		constraintFns.add(cleanFn);
		constraintFns.add(changesFn);

		Pair<Objective, Set<Objective>> p = new Pair<Objective, Set<Objective>>();
		p.setO1(pvtFn);
		p.setO2(constraintFns);

		return p;
	}
	
	/*
	 * run the whole data cleaning process with parameter
	 */
	public String runDataCleaning (TargetDataset tgtDataset, MasterDataset mDataset, float simThreshold, SearchType searchType, Map<String, Double> params) {
		String result = "";
		StringBuilder sb = new StringBuilder(result);
		
		List<Constraint> constraints = tgtDataset.getConstraints();
		System.out.println("--------------Data Cleaning------------------");
		System.out.println("Target Dataset: " + tgtDataset.toString());
		System.out.println("Master Dataset: " + mDataset.toString());
		System.out.println("simThreshold: " + simThreshold);
		System.out.println("searchType: " + searchType.toString());
		
		for (Constraint constraint: constraints) {
			System.out.println("--------------------------------------------");
			System.out.println("Constraint: " + constraint.toString());
			
			sb.append("Constraint: " + constraint.toString() + "\n");
			
			Violations vios = findVios(tgtDataset, constraint);
			System.out.println("Violations: " + vios);
			
			List<Record> vioRecs = getVioRecords(tgtDataset, vios);
			System.out.println("Violation Records: " + vioRecs);
			
			List<Match> matches = getMatching(constraint, vioRecs, mDataset.getRecords(), simThreshold, 
					tgtDataset.getName(), mDataset.getName());
			System.out.println("Matching: " + matches);
			
			Search search;
			if (searchType == SearchType.SA_WEIGHTED) {
				if (params == null) {
					search = initWeightedSimulAnneal(mDataset, constraint);
				}
				else {
					double stTemp = params.get("stTemp");
					double endTemp = params.get("endTemp");
					double alpTemp = params.get("alpTemp");
					double bestEn = params.get("bestEn");
					double alphaPvt = params.get("alphaPvt");
					double betaInd = params.get("betaInd");
					double gamaSize = params.get("gamaSize");
					
					search = initWeightedSimulAnneal(mDataset, constraint, stTemp, endTemp, alpTemp, bestEn, 
							alphaPvt, betaInd, gamaSize);
				}
			}
			else if (searchType == SearchType.SA_EPS_DYNAMIC) {
				if (params == null) {
					search = initDynamicSimulAnneal(mDataset, constraint);
				}
				else {
					double stTemp = params.get("stTemp");
					double endTemp = params.get("endTemp");
					double alpTemp = params.get("alpTemp");
					double bestEn = params.get("bestEn");
					double cleaning = params.get("cleaning");
					double size = params.get("size");
					
					search = initDynamicSimulAnneal(mDataset, constraint, stTemp, endTemp, alpTemp, bestEn, 
							cleaning, size);
				}
			}
			else if (searchType == SearchType.SA_EPS_LEX) {
				if (params == null) {
					search = initLexicalSimulAnneal(mDataset, constraint);
				}
				else {
					double stTemp = params.get("stTemp");
					double endTemp = params.get("endTemp");
					double alpTemp = params.get("alpTemp");
					double bestEn = params.get("bestEn");
					double privacy = params.get("privacy");
					double cleaning = params.get("cleaning");
					
					search = initLexicalSimulAnneal(mDataset, constraint, stTemp, endTemp, alpTemp, bestEn,
							privacy, cleaning);
				}
			}
			else if (searchType == SearchType.SA_EPS_FLEX) {
				if (params == null) {
					search = initFlexibleSimulAnneal(mDataset, constraint);
				}
				else {
					double stTemp = params.get("stTemp");
					double endTemp = params.get("endTemp");
					double alpTemp = params.get("alpTemp");
					double bestEn = params.get("bestEn");
					double cleaning = params.get("cleaning");
					double size = params.get("size");
					
					search = initFlexibleSimulAnneal(mDataset, constraint, stTemp, endTemp, alpTemp, bestEn, 
							cleaning, size);
				}
			}
			else {
				search = initSimulAnneal(mDataset, constraint);
			}
			
			Set<Candidate> candidates = getRecommendations (constraint, matches, search, tgtDataset, mDataset, true);
			System.out.println(candidates.toString());
			
			sb.append(candidates.toString() + "\n");
			sb.append("\n");
		}
		
		result = sb.toString();
		return result;
	}
	
	/*
	 * run the whole data cleaning process without parameter, using default paramters
	 */
	public String runDataCleaning (TargetDataset tgtDataset, MasterDataset mDataset, float simThreshold, SearchType searchType) {
		return runDataCleaning(tgtDataset, mDataset, simThreshold, searchType, null);
	}
	
	/*
	 * find violations w.r.t the constraint in the dataset
	 */
	public Violations findVios (Dataset tgtDataset, Constraint constraint) {
		List<Record> records = tgtDataset.getRecords();
		Violations allViols = repairService.calcViolations(records, constraint);
		return allViols;
	}
	
	/**
	 * output the violations in a specific format
	 * @return
	 */
	public String outputViolations (Violations v, Constraint con) {
		StringBuilder sb = new StringBuilder();
		
		List<String> cols = con.getColsInConstraint();
		
		Multimap<String, Record> map = v.getViolMap();
		for (Record r: map.values()) {
			String s = r.prettyPrintRecord(cols);
			sb.append(s + "\n");
		}
		
		return sb.toString();
	}
	
	/**
	 * output the violations in a specific format
	 * @return
	 */
	public List<List<String>> outputViolationsList (Violations v, Constraint con) {
		List<List<String>> result = new ArrayList<List<String>>();
		
		List<String> cols = con.getColsInConstraint();
		
		Multimap<String, Record> map = v.getViolMap();
		
		for (Record r: map.values()) {
			String s = r.getRecordStr(cols, ",");
			String[] sArray = s.split(",");
			List<String> sList = new ArrayList<String>();
			sList.add(Long.toString(r.getId()));
			sList.addAll(Arrays.asList(sArray));
			result.add(sList);
		}
		
		return result;
	}
	
	//get list of records that have violations in the original dataset
	private List<Record> getVioRecords(Dataset originalDataset, Violations vios) {
		List<Set<Long>> viosList = repairService.subsetViolsBySize(vios);
		List<Record> records = new ArrayList<Record>();

		for (Set<Long> viols : viosList) {
			for (long v : viols) {
				records.add(originalDataset.getRecord(v));
			}
		}

		return records;
	}
	
	/*
	 * find matching between two datasets
	 */
	public List<Match> getMatching (Constraint constraint,
			List<Record> tgtRecords, List<Record> mRecords, float simThreshold,
			String tgtFileName, String mFileName) {
		List<Match> tgtMatches = matchingService.applyApproxDataMatching(
				constraint, tgtRecords, mRecords, simThreshold,
				tgtFileName, mFileName);

		return tgtMatches;
	}
	
	/*
	 * Load target dataset
	 */
	public TargetDataset loadTargetDataset (String tgtUrl, String tgtFileName,
			String fdUrl, char separator, char quoteChar) {
		return datasetService.loadTargetDataset(tgtUrl, tgtFileName, fdUrl, separator, quoteChar);
	}
	
	/*
	 * Load master dataset
	 */
	public MasterDataset loadMasterDataset (String mUrl, String mFileName,
			String fdUrl, long targetId, char separator, char quoteChar) {
		return datasetService.loadMasterDataset(mUrl, mFileName, fdUrl, targetId, separator, quoteChar);
	};
	
	/*
	 * get list of recommendations w.r.t the list of matching results and searching objective & type
	 */
	public Set<Candidate> getRecommendations (Constraint constraint,
			List<Match> tgtMatches, Search search, TargetDataset tgtDataset,
			MasterDataset mDataset, boolean shdReturnInit) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);
		return repairService.calcOptimalSolns(constraint, tgtMatches, search, tgtDataset, mDataset, table, shdReturnInit);
	}
	
	/*
	 * run the whole data cleaning process with parameter
	 */
	public Map<Constraint, Set<Candidate>> runDataCleaningMap (TargetDataset tgtDataset, MasterDataset mDataset, float simThreshold, SearchType searchType, Map<String, Double> params) {
		Map<Constraint, Set<Candidate>> result = new HashMap<Constraint, Set<Candidate>>();
		
		List<Constraint> constraints = tgtDataset.getConstraints();
		System.out.println("--------------Data Cleaning------------------");
		System.out.println("Target Dataset: " + tgtDataset.toString());
		System.out.println("Master Dataset: " + mDataset.toString());
		System.out.println("simThreshold: " + simThreshold);
		System.out.println("searchType: " + searchType.toString());
		
		for (Constraint constraint: constraints) {
			System.out.println("--------------------------------------------");
			System.out.println("Constraint: " + constraint.toString());
			
			Violations vios = findVios(tgtDataset, constraint);
			System.out.println("Violations: " + vios);
			
			List<Record> vioRecs = getVioRecords(tgtDataset, vios);
			System.out.println("Violation Records: " + vioRecs);
			
			List<Match> matches = getMatching(constraint, vioRecs, mDataset.getRecords(), simThreshold, 
					tgtDataset.getName(), mDataset.getName());
			System.out.println("Matching: " + matches);
			
			Search search;
			if (searchType == SearchType.SA_WEIGHTED) {
				if (params == null) {
					search = initWeightedSimulAnneal(mDataset, constraint);
				}
				else {
					double stTemp = params.get("stTemp");
					double endTemp = params.get("endTemp");
					double alpTemp = params.get("alpTemp");
					double bestEn = params.get("bestEn");
					double alphaPvt = params.get("alphaPvt");
					double betaInd = params.get("betaInd");
					double gamaSize = params.get("gamaSize");
					
					search = initWeightedSimulAnneal(mDataset, constraint, stTemp, endTemp, alpTemp, bestEn, 
							alphaPvt, betaInd, gamaSize);
				}
			}
			else if (searchType == SearchType.SA_EPS_DYNAMIC) {
				if (params == null) {
					search = initDynamicSimulAnneal(mDataset, constraint);
				}
				else {
					double stTemp = params.get("stTemp");
					double endTemp = params.get("endTemp");
					double alpTemp = params.get("alpTemp");
					double bestEn = params.get("bestEn");
					double cleaning = params.get("cleaning");
					double size = params.get("size");
					
					search = initDynamicSimulAnneal(mDataset, constraint, stTemp, endTemp, alpTemp, bestEn, 
							cleaning, size);
				}
			}
			else if (searchType == SearchType.SA_EPS_LEX) {
				if (params == null) {
					search = initLexicalSimulAnneal(mDataset, constraint);
				}
				else {
					double stTemp = params.get("stTemp");
					double endTemp = params.get("endTemp");
					double alpTemp = params.get("alpTemp");
					double bestEn = params.get("bestEn");
					double privacy = params.get("privacy");
					double cleaning = params.get("cleaning");
					
					search = initLexicalSimulAnneal(mDataset, constraint, stTemp, endTemp, alpTemp, bestEn,
							privacy, cleaning);
				}
			}
			else if (searchType == SearchType.SA_EPS_FLEX) {
				if (params == null) {
					search = initFlexibleSimulAnneal(mDataset, constraint);
				}
				else {
					double stTemp = params.get("stTemp");
					double endTemp = params.get("endTemp");
					double alpTemp = params.get("alpTemp");
					double bestEn = params.get("bestEn");
					double cleaning = params.get("cleaning");
					double size = params.get("size");
					
					search = initFlexibleSimulAnneal(mDataset, constraint, stTemp, endTemp, alpTemp, bestEn, 
							cleaning, size);
				}
			}
			else {
				search = initSimulAnneal(mDataset, constraint);
			}
			
			Set<Candidate> candidates = getRecommendations (constraint, matches, search, tgtDataset, mDataset, true);
			System.out.println(candidates.toString());
			
			result.put(constraint, candidates);
		}
		
		return result;
	}
	
	public Map<Constraint, Set<Candidate>> runDataCleaningMap (TargetDataset tgtDataset, MasterDataset mDataset, float simThreshold, SearchType searchType) {
		return runDataCleaningMap(tgtDataset, mDataset, simThreshold, searchType, null);
	}
	
	public static void main (String[] args) {
		DataCleaningUtilsImpl dcu = new DataCleaningUtilsImpl();
		
		char separator = ',';
		char quoteChar = '"';
		
		String tgtUrl = "/Users/thomas/Documents/Programming/DataPrivacy/resource/data/testdata1/addressTarget.csv";
		String tgtFileName = "addressTarget";

		String mUrl = "/Users/thomas/Documents/Programming/DataPrivacy/resource/data/testdata1/addressMaster.csv";
		String mFileName = "addressMaster";
		
		String fdUrl = "/Users/thomas/Documents/Programming/DataPrivacy/resource/data/testdata1/address_fd.csv";
		
		TargetDataset target = dcu.loadTargetDataset(tgtUrl, tgtFileName, fdUrl, separator, quoteChar);
		MasterDataset master = dcu.loadMasterDataset(mUrl, mFileName, fdUrl, 1, separator, quoteChar);
		
//		dcu.runDataCleaning(target, master, target.getConstraints().get(0));
		dcu.runDataCleaning(target, master, 0.8f, SearchType.SA_WEIGHTED);
	}
	
}
