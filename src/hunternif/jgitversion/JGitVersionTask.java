package hunternif.jgitversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.property.ParseProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.gitective.core.CommitFinder;
import org.gitective.core.CommitUtils;
import org.gitective.core.filter.commit.CommitCountFilter;

public class JGitVersionTask extends Task {
	private String dir;
	private String versionProperty;
	private String shaProperty;
	private String branchProperty;
	private String funnameProperty;
	private String adjFile;
	private String nameFile;
	private String propertiesFileName;
	private String baseBranch;
	
	public void setDir(String dir) {
		this.dir = dir;
	}

	
	public void setBaseBranch(String baseBranch) {
		this.baseBranch = baseBranch;
	}

	public void setVersionString(String property) {
		this.versionProperty = property;
	}
	

	public void setSha1String(String property) {
		this.shaProperty = property;
	}
	
	public void setFunNameString(String property) {
		this.funnameProperty = property;
	}


	public void setBranchString(String property) {
		this.branchProperty = property;
	}

	public void setNameFile(String nameFile) {
		this.nameFile = nameFile;
	}

	public void setPropertiesFileName(String nameFile) {
		this.propertiesFileName= nameFile;
	}

	public void setAdjectiveFile(String adjFile) {
		this.adjFile = adjFile;
	}

	@Override
	public void execute() throws BuildException {
		try {
            System.out.println("dir="+dir+"=");
    		git = Git.open(new File(dir));
    		repo = git.getRepository();
    		String branch = repo.getBranch();
    		RevCommit latestCommit = CommitUtils.getHead(repo);
			String version = getProjectVersion(repo, git, baseBranch);
			List<String> adjs = readWords(this.adjFile);
			List<String> names = readWords(this.nameFile);
		
    		Iterable<RevCommit> log = git.log().call();
    		log.iterator().next();

			String lastCommit = latestCommit.name();
			int i = Integer.parseInt(lastCommit.substring(0, 5),16);
			String adj = adjs.get(i % adjs.size());

			int ll = lastCommit.length();
			int j = Integer.parseInt(lastCommit.substring(6, 10),16);
			String name = names.get(j % names.size());

			Project project = getProject();
			if (project != null) {
				project.setProperty(versionProperty, version);
				project.setProperty(branchProperty, branch);
				project.setProperty(this.shaProperty,latestCommit.name());
				project.setProperty(this.funnameProperty, adj+" "+name);
			}
			HashMap<String, String> pairs = extractKeyValuePairsFromTags(repo, git, baseBranch);
			for (String key: pairs.keySet()){
				String value = pairs.get(key);
				if ((value != null) && !(value.equalsIgnoreCase("")))
					System.out.println(key+" = "+value);
			}
		} catch (Exception e) {
			throw new BuildException(e);
		}
	}
	

    public List<String> readWords(String fileName) {
    	if ((fileName == null) || (fileName.equalsIgnoreCase("")))
    			return null;
        List<String> list = new ArrayList<String>();
        File file = new File(fileName);
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(file));
            String text = null;

            while ((text = reader.readLine()) != null) {
                String[] adjs = text.split(" ", 8);
                if (!isEmpty(adjs[0]))
                    list.add(adjs[0]);
                if(adjs.length >1) {
                    if (!isEmpty(adjs[1]))
                        list.add(adjs[adjs.length-1]);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
            }
        }
        return list;
    }

    public static boolean isEmpty(CharSequence str) {
        if (str == null || str.length() == 0)
            return true;
        else
            return false;
    }



    private Repository repo;
	private Git git;
    

	public static String getProjectVersion1(File repoDir) throws IOException, GitAPIException {
    		Git git = Git.open(repoDir);
    		Repository repo = git.getRepository();
    		return getProjectVersion(repo, git, "master");
	}

	public static String getProjectVersion(Repository repo, Git git, String baseBranch)
	    throws IOException, GitAPIException {
		
		// Find base commit between current branch and baseBranch.
		// baseBranch is typically "master" but could be something else e.g. "develop"
		
		Ref masterRef=repo.getRef("master");
		String branch = repo.getBranch();
        System.out.println("baseBranch="+baseBranch+"=");
        System.out.println("branch="+branch+"=");
		Ref baseRef = repo.getRef(baseBranch);
        System.out.println("baseRef="+baseRef.getObjectId().getName()+"=");
		RevCommit base = CommitUtils.getBase(repo, baseBranch, branch);
        System.out.println("base="+base.getName()+"=");
		//RevCommit base = CommitUtils.getBase(repo, baseRef.getObjectId().getName(), branch);
		CommitCountFilter count = new CommitCountFilter();
		CommitFinder finder = new CommitFinder(repo).setFilter(count);
		finder.findBetween(branch, base);
		long commitsSinceBase = count.getCount();
        System.out.println("commitsSinceBase="+commitsSinceBase);

		// Find tags in baseBranch before base commit:
		RevWalk rw = new RevWalk(repo);
		rw.markStart(base);
		rw.setRetainBody(false);
		List<Ref> masterAsList = Arrays.asList(baseRef);
		List<Ref> tags = git.tagList().call();
        //System.out.println("====== tags ======");
		//for (Ref tag : tags) { System.out.println(tag.getName()); }
        //System.out.println(" ");


		Map<RevCommit, Ref> masterTags = new HashMap<RevCommit, Ref>();
		for (Ref tag : tags) {
			tag = repo.peel(tag);
			ObjectId commitID = tag.getPeeledObjectId();
			//System.out.println("found tag="+tag);
			//System.out.println("found tag.getName()="+tag.getName());
			//System.out.println("found tag.getLeaf().getName()="+tag.getLeaf().getName());
			//System.out.println("found tag.getObjectId().getName()="+tag.getObjectId().getName());
			//System.out.println("found commitID="+commitID);
			if (commitID == null) {
				//this is a lightweight tag
				commitID=tag.getObjectId();
			}

			if (commitID != null) {
				RevCommit commit = rw.parseCommit(commitID);
				// Only remember tags reachable from "baseBranch":
				if (!RevWalkUtils.findBranchesReachableFrom(commit, rw, masterAsList).isEmpty()) {
					//System.out.println("adding tag="+tag.getName());
					masterTags.put(commit, tag);
				}
				else {
					masterTags.put(commit, tag);
				}
			}
		}

		// Find the shortest distance in commits between base tag in "master":
		long commitsBetweenBaseAndTag=Long.MAX_VALUE;
		String tagName = "";
		for (RevCommit tagCommit : masterTags.keySet()) {
			count.reset();
			finder.findBetween(base, tagCommit);
			if (count.getCount() < commitsBetweenBaseAndTag) {
				commitsBetweenBaseAndTag = count.getCount();
				tagName = masterTags.get(tagCommit).getName();
			}
		}

		if (commitsBetweenBaseAndTag == Long.MAX_VALUE) {
			// If no tag, get total number of commits:
			commitsBetweenBaseAndTag = repo.getRefDatabase().getRefs("").size();
		}
		long commitsSinceLastMasterTag = commitsSinceBase + commitsBetweenBaseAndTag;
		
		// Construct version string:
		String version = branch.equals(baseBranch) ? "" : (branch + "-");
		System.out.println(tagName);
		if (tagName.startsWith("refs/tags/")) {
			tagName = tagName.substring("refs/tags/".length());
		}

		System.out.println(tagName);
		// v1.1 -> 1.1
		if (tagName.matches("v\\d+.*")) {
			tagName = tagName.substring(1);
		}
		//System.out.println(tagName);
		
		// ver1.1 -> 1.1
		if (tagName.matches("ver\\d+.*")) {
			tagName = tagName.substring(3);
		}
		//System.out.println(tagName);
		
		// Wawa-Version-1.1 -> 1.1
		if (tagName.matches("(?i)wawa-version-\\d+.*")) {
			tagName = tagName.substring(13);
		}
		//System.out.println(tagName);

		if (tagName.isEmpty()) {
			version = "0";
		}
		version += tagName + "." + commitsSinceLastMasterTag;
		System.out.println(version);
		
		return version;
	}

	public static class RefDateComparator implements Comparator<Ref> {
		RevWalk localwalk;
		public RefDateComparator(Repository repo) {
			this.localwalk = new RevWalk(repo);
		}
		@Override
		public int compare(Ref o1, Ref o2) {
			Date date1=null;
			Date date2=null;
			try {
				final RevObject obj1 = localwalk.parseAny(o1.getObjectId());
				final RevObject obj2 = localwalk.parseAny(o2.getObjectId());
				if (obj1 instanceof RevCommit) {
					RevCommit tagCommit = (RevCommit) obj1;
					date1=tagCommit.getAuthorIdent().getWhen();
				} else if (obj1 instanceof RevTag) {
					RevTag tagr = (RevTag) obj1;
					date1=tagr.getTaggerIdent().getWhen();
				}
				if (obj2 instanceof RevCommit) {
					RevCommit tagCommit = (RevCommit) obj2;
					date2=tagCommit.getAuthorIdent().getWhen();
				} else if (obj2 instanceof RevTag) {
					RevTag tagr = (RevTag) obj2;
					date2=tagr.getTaggerIdent().getWhen();
				}
			} catch (MissingObjectException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if ((date1 == null) && (date2 == null))
				return 0;
			if (date1 == null)
				return 1;
			if (date2 == null)
				return -1;
			return (int) (date1.getTime() - date2.getTime());
		}
	};
	public static HashMap<String, String> extractKeyValuePairsFromTags(Repository repo, Git git, String baseBranch)
			throws IOException, GitAPIException {
		HashMap<String, String> pairs = new HashMap<String,String>();
		RevCommit latestCommit = CommitUtils.getHead(repo);
		RevCommit commit;
		RevWalk walk = new RevWalk(repo);
		walk.markStart(latestCommit);
        while ((commit=walk.next()) != null){ 
			//System.out.println("commit="+commit.getName());
			try {
				List<Ref> tagset = getTagsForCommit(repo, commit, repo.getTags().values());
				RefDateComparator dateComp = new RefDateComparator(repo) ;
				Collections.sort(tagset, dateComp);
				for (Ref tag: tagset){
					final RevObject obj = walk.parseAny(tag.getObjectId());
					Date date=null;
					if (obj instanceof RevCommit) {
						RevCommit tagCommit = (RevCommit) obj;
						date=tagCommit.getAuthorIdent().getWhen();
					} else if (obj instanceof RevTag) {
						RevTag tagr = (RevTag) obj;
						date=tagr.getTaggerIdent().getWhen();
					}

					//System.out.println("found tag="+tag.getName() + " for commit="+commit.getName());
					//System.out.println("date="+date);
					Properties p = parsePropertiesString(tag.getName());
					for (final String name: p.stringPropertyNames())
						if (pairs.get(name) == null)
							pairs.put(name, p.getProperty(name));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
         }
		return pairs;
    }
	
	// mimic git tag --contains <commit>
	private static List<Ref> getTagsForCommit(Repository repo, RevCommit commit, Collection<Ref> tagRefs)
		throws Exception {
		final List<Ref> tags = new ArrayList<Ref>();
		final RevWalk walk = new RevWalk(repo);
		walk.reset();
		for (final Ref ref : tagRefs) {
			final RevObject obj = walk.parseAny(ref.getObjectId());
			final RevCommit tagCommit;
			if (obj instanceof RevCommit) {
				tagCommit = (RevCommit) obj;
			} else if (obj instanceof RevTag) {
				tagCommit = walk.parseCommit(((RevTag) obj).getObject());
			} else {
				continue;
			}
			if (commit.equals(tagCommit) || walk.isMergedInto(commit, tagCommit)) {
				tags.add(ref);
			}
		}
		return tags;
	} 
	public  static Properties parsePropertiesString(String s) {
	    // grr at load() returning void rather than the Properties object
	    // so this takes 3 lines instead of "return new Properties().load(...);"
		String s1 = s.replaceAll("refs/tags/", "");
		String s2 = s.replaceAll("Wawa-Version-", "");
		
	    final Properties p = new Properties();
	    p.setProperty("versionk", s2);
	    try {
	    	if ((s1 != null) && !(s1.equalsIgnoreCase("")))
	    		p.load(new StringReader(s1));
		} catch (IOException e) {
			e.printStackTrace();
		}
	    return p;
	}
}
