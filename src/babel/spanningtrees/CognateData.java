package babel.spanningtrees;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CognateData {

    // map GlossID to Gloss
	Map<Integer, String> mapGlossIDtoMeaningClassName;
    // map GlossID to map of MultistateCode to Cognate
	Map<Integer, Map<Integer,Cognate>> cognateGlossMap;
	
	String getMeaningClassName(int GlossID) {
		return mapGlossIDtoMeaningClassName.get(GlossID);
	}
	
	Map<Integer,Cognate> getCognates(int GlossID) {
		return cognateGlossMap.get(GlossID);
	}
	
	void loadCognateData(String fileName) throws Exception {
		System.err.println("Loading " + fileName);
		mapGlossIDtoMeaningClassName = new HashMap<Integer, String>();
		cognateGlossMap = new HashMap<Integer, Map<Integer,Cognate>>();

		List<Entry> entries = CognateIO.readCognates(new File(fileName));
		
		for (Entry entry : entries) {
			mapGlossIDtoMeaningClassName.put(entry.GlossID, entry.Gloss);
			if (cognateGlossMap.containsKey(entry.GlossID)) {
				Map<Integer, Cognate> cognateMap = cognateGlossMap.get(entry.GlossID);
				if (cognateMap.containsKey(entry.MultistateCode)) {
					Cognate cognate = cognateMap.get(entry.MultistateCode);
					cognate.languages.add(entry.Language);
					cognate.word.add(entry.Word);
				} else {
					Cognate cognate = new Cognate();
					cognate.GlossID = entry.GlossID;
					cognate.MultistateCode = entry.MultistateCode;
					cognate.languages.add(entry.Language);
					cognate.word.add(entry.Word);
					cognateMap.put(entry.MultistateCode, cognate);
				}				
			} else {
				Map<Integer, Cognate> cognateMap = new HashMap<Integer, Cognate>();
				Cognate cognate = new Cognate();
				cognate.GlossID = entry.GlossID;
				cognate.MultistateCode = entry.MultistateCode;
				cognate.languages.add(entry.Language);
				cognate.word.add(entry.Word);
				cognateMap.put(entry.MultistateCode, cognate);
				cognateGlossMap.put(entry.GlossID, cognateMap);
			}
		}
	}
	
	void calcSpanningTrees(Map<String,Location> locations) {
		for (Map<Integer, Cognate> cognateMap : cognateGlossMap.values()) {
			List<Cognate> cognates = new ArrayList<Cognate>();
			cognates.addAll(cognateMap.values());
			for (Cognate cognate : cognates) {
				List<Cognate> splitCognates = calcSpanningTree(cognate, locations, cognateMap.keySet());
				for (Cognate c : splitCognates) {
					cognateMap.put(c.MultistateCode, c);
				}
			}
		}
	}

	
	
	private List<Cognate> calcSpanningTree(Cognate cognate, Map<String, Location> locations, Set<Integer> MultistateCodes) {
		List<Cognate> splitCognates = new ArrayList<Cognate>();
//		if (cognate.MultistateCode == 0) {
//			// missing data
//			splitCognates.add(cognate);
//			return splitCognates;
//		}
		// collect locations associated with languages
		List<Location> locs = new ArrayList<Location>();
		for (String language : cognate.languages) {
			Location loc = locations.get(language);
			if (loc == null) {
				loc = locations.get(language);
			}
			locs.add(loc);
		}
		// create distance matrix
		double [][] dist = new double[locs.size()][locs.size()];
		for (int i = 0; i < locs.size(); i++) {
			for (int j = i+1; j < locs.size(); j++) {
				dist[i][j] = distance(locs.get(i), locs.get(j));
//				double averageLong = (locs.get(i).longitude + locs.get(j).longitude) / 2.0;
//				double delta = 500 * (averageLong - 113.7199742531)/(153.5701790672 - 113.7199742531);
//				dist[i][j] += delta;
//				dist[j][i] = dist[i][j]; 
			}
		}
		// find  spanning tree
		int[] group = new int[locs.size()];
		for (int i = 0; i < group.length; i++) {
			group[i] = i;
		}
		double minDist = 0;
		boolean progress = true;

//		     longitude       latitude         
//		max 153.5701790672	-9.9422964683
//		min 113.7199742531	-38.471266
// distance(x,y) = GreatCircleDistance(x,y) + 500 * (1.0-(long(x)+long(y))/2 - 113.7199742531)/(153.5701790672-113.7199742531))  		
	
		for (int i = 0; i < group.length-1 && progress; i++) {
			progress = false;
			// find minimum distance
			minDist = Double.MAX_VALUE;
			int min1 = -1; int min2 = -1;
			for (int x = 0; x < group.length; x++) {
				for (int y = x + 1; y < group.length; y++) {
					if (group[x] != group[y] && dist[x][y] < minDist) {
						minDist = dist[x][y];
						min1 = x;
						min2 = y;
					}
					
				}
			}
			if (minDist < CognateIO.COGNATE_SPLIT_THRESHOLD) {
				progress = true;
				int g = group[min2];
				for (int x = 0; x < group.length; x++) {
					if (group[x] == g) {
						group[x] = group[min1];
					}
				}
				cognate.edges.add(min1);
				cognate.edges.add(min2);
			}
		}
		// assemble cognates
		if (minDist < CognateIO.COGNATE_SPLIT_THRESHOLD) {
			splitCognates.add(cognate);
			return splitCognates;
		}
		
		Cognate[] splits = new Cognate[group.length];
		//MultistateCodes.remove(cognate.MultistateCode);
		Set<Integer> dup = new HashSet<Integer>();
		dup.addAll(MultistateCodes);
		dup.remove(cognate.MultistateCode);
		for (int i = 0; i < group.length; i++) {
			if (splits[group[i]] == null) {
				splits[group[i]] = new Cognate();
				splits[group[i]].GlossID = cognate.GlossID;
				splits[group[i]].MultistateCode = cognate.MultistateCode;
				while (dup.contains(splits[group[i]].MultistateCode)) {
					splits[group[i]].MultistateCode++;
				}
				Integer code = splits[group[i]].MultistateCode;
				dup.add(code);
				splitCognates.add(splits[group[i]]);
			}
			splits[group[i]].languages.add(cognate.languages.get(i));
			splits[group[i]].word.add(cognate.word.get(i));			
		}
		for (int i = 0; i < cognate.edges.size(); i+= 1) {
			int edge = cognate.edges.get(i);
			int groupID = group[edge];
			String language = cognate.languages.get(edge);
			int edgeID = splits[groupID].languages.indexOf(language);
			splits[groupID].edges.add(edgeID);
		}
		
		// print to stdout
//		DecimalFormat format = new DecimalFormat("####");
//		System.out.print(cognate.GlossID + " " + getGloss(cognate.GlossID) + " " + cognate.MultistateCode + " " + format.format(minDist) + " " + splitCognates.size());
//		for (Cognate c: splitCognates) {
//			System.out.print(" (");
//			for (int k = 0; k < c.languages.size(); k++) {
//				System.out.print(c.languages.get(k) + " " + c.word.get(k++));
//				if (k < c.languages.size() - 1) {
//					System.out.print(", ");
//				}
//			}
//			System.out.print(")");
//		}
//		System.out.println();

		
		DecimalFormat format = new DecimalFormat("####");
		//System.out.print(cognate.GlossID + " " + getLink(splitCognates.get(0).word.get(0)) + " " + format.format(minDist) + " " + splitCognates.size());
		for (Cognate c: splitCognates) {
			System.out.print(" (");
			for (int k = 0; k < c.languages.size(); k++) {
				System.out.print(c.languages.get(k));
				if (k < c.languages.size() - 1) {
					System.out.print(", ");
				}
			}
			System.out.print(")");
		}
		System.out.println();
		return splitCognates;
	}

	private String getLink(String word) {
		String link = "<a href='http://ielex.mpi.nl/";
		String [] strs = word.split("_");
		strs[2] = strs[2].replaceAll(",", "");
		link += strs[1] + "/" + strs[2] + "'>" + strs[0] + " " + strs[2] + "</a>";
		return link;
	}

	static public double distance(Location location, Location location2) {
		// great cirlce distance
		double fLat1 = location.latitude;
		double fLong1 = location.longitude;
		double fLat2 = location2.latitude;
		double fLong2 = location2.longitude;
		fLat1 *= Math.PI/180.0;
		fLat2 *= Math.PI/180.0;
		fLong1 *= Math.PI/180.0;
		fLong2 *= Math.PI/180.0;
		double fDist = 6371.01*Math.acos(Math.sin(fLat1)*Math.sin(fLat2) + Math.cos(fLat1)*Math.cos(fLat2)*Math.cos(fLong1-fLong2));
		return fDist;
	}
	
}
