package org.jboss.mjolnir.client.domain;

import org.jboss.mjolnir.authentication.KerberosUser;

import java.io.Serializable;

/**
 * Domain object representing user subscribed to GitHub organization.
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class Subscription implements Serializable {

    private String gitHubName;
    private KerberosUser kerberosUser;
    private boolean activeKerberosAccount;

    public String getGitHubName() {
        return gitHubName;
    }

    public void setGitHubName(String gitHubName) {
        this.gitHubName = gitHubName;
    }

    public KerberosUser getKerberosUser() {
        return kerberosUser;
    }

    public void setKerberosUser(KerberosUser kerberosUser) {
        this.kerberosUser = kerberosUser;
    }

    public boolean isActiveKerberosAccount() {
        return activeKerberosAccount;
    }

    public void setActiveKerberosAccount(boolean hasActiveKerberosAccount) {
        this.activeKerberosAccount = hasActiveKerberosAccount;
    }

    public String getKerberosName() {
        return kerberosUser != null ? kerberosUser.getName() : null;
    }

    public boolean isWhitelisted() {
        return kerberosUser != null && kerberosUser.isWhitelisted();
    }

    @Override
    public String toString() {
        return "Subscription{" +
                "gitHubName='" + gitHubName + '\'' +
                ", kerberosUser=" + kerberosUser +
                ", activeKerberosAccount=" + activeKerberosAccount +
                '}';
    }

    /*@Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subscription)) return false;

        Subscription that = (Subscription) o;

        if (gitHubName != null ? !gitHubName.equals(that.gitHubName) : that.gitHubName != null) return false;
        if (kerberosUser != null ? !kerberosUser.equals(that.kerberosUser) : that.kerberosUser != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = gitHubName != null ? gitHubName.hashCode() : 0;
        result = 31 * result + (kerberosUser != null ? kerberosUser.hashCode() : 0);
        return result;
    }*/
}
