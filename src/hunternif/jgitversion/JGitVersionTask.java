package hunternif.jgitversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
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
    

	public static String getProjectVersion(File repoDir) throws IOException, GitAPIException {
    		Git git = Git.open(repoDir);
    		Repository repo = git.getRepository();
    		return getProjectVersion(repo, git, "master");
	}
	public static String getProjectVersion(Repository repo, Git git, String baseBranch)
	throws IOException, GitAPIException {
		
		// Find base commit between current branch and baseBranch.
		// baseBranch is typically "master" but could be something else e.g. "develop"
		
		String branch = repo.getBranch();
        System.out.println("baseBranch="+baseBranch+"=");
        System.out.println("branch="+branch+"=");
		Ref baseRef = repo.getRef(baseBranch);
        System.out.println("baseRef="+baseRef.getObjectId().getName()+"=");
		RevCommit base = CommitUtils.getBase(repo, baseBranch, branch);
		//RevCommit base = CommitUtils.getBase(repo, baseRef.getObjectId().getName(), branch);
		CommitCountFilter count = new CommitCountFilter();
		CommitFinder finder = new CommitFinder(repo).setFilter(count);
		finder.findBetween(branch, base);
		long commitsSinceBase = count.getCount();
		
		// Find tags in baseBranch before base commit:
		RevWalk rw = new RevWalk(repo);
		rw.markStart(base);
		rw.setRetainBody(false);
		List<Ref> masterAsList = Arrays.asList(baseRef);
		List<Ref> tags = git.tagList().call();
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
			RevCommit commit = rw.parseCommit(commitID);
			// Only remember tags reachable from "baseBranch":
			if (!RevWalkUtils.findBranchesReachableFrom(commit, rw, masterAsList).isEmpty()) {
				//System.out.println("adding tag="+tag.getName());
				masterTags.put(commit, tag);
			}
		}
		
		// Find the shortest distance in commits between base tag in "master":
		long commitsBetweenBaseAndTag = Long.MAX_VALUE;
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
		//System.out.println(tagName);
		if (tagName.startsWith("refs/tags/")) {
			tagName = tagName.substring("refs/tags/".length());
		}
		// v1.1 -> 1.1
		if (tagName.matches("v\\d+.*")) {
			tagName = tagName.substring(1);
		}
		
		// ver1.1 -> 1.1
		if (tagName.matches("ver\\d+.*")) {
			tagName = tagName.substring(3);
		}
		
		// Wawa-Version-1.1 -> 1.1
		if (tagName.matches("(?i)wawa-version-\\d+.*")) {
			tagName = tagName.substring(13);
		}

		if (tagName.isEmpty()) {
			version = "0";
		}
		version += tagName + "." + commitsSinceLastMasterTag;
		
		return version;
	}
}
