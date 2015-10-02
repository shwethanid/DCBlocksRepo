package messages;

import java.io.Serializable;

import akka.actor.ActorRef;
import akka.cluster.Member;

/**
 * Member Status message
 */
public class memStatus implements Serializable{

	private static final long serialVersionUID = -5443261356732908320L;
	private Integer gid;
	private Member member;
	private Integer nid;
	private Boolean isFailed = false;
	private ActorRef ref;

	public Member getMember() {
		return member;
	}

	public void setMember(Member member) {
		this.member = member;
	}
	public Integer getGId() {
		return gid;
	}

	public void setGId(Integer gid) {
		this.gid = gid;
	}

	public Integer getId() {
		return nid;
	}

	public void setId(Integer nid) {
		this.nid = nid;
	}

	public Boolean getIsFailed() {
		return isFailed;
	}

	public void setIsFailed(Boolean isFailed) {
		this.isFailed = isFailed;
	}

	public ActorRef getRef() {
		return ref;
	}

	public void setRef(ActorRef ref) {
		this.ref = ref;
	}
}
