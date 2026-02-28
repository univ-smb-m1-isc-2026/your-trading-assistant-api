package fr.info803.trading_assistant.entity;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/*
    Entité JPA représentant un compte utilisateur.

    Implémente UserDetails pour s'intégrer nativement à Spring Security.

    Particularité importante : le champ "username" (nom d'affichage) entre en conflit
    avec UserDetails.getUsername() que Spring Security utilise comme identifiant.
    Pour éviter l'ambiguïté :
      - getUsername()        → retourne l'EMAIL (identifiant de connexion pour Spring Security)
      - getDisplayUsername() → retourne le NOM D'AFFICHAGE (ce qu'on inclut dans le JWT)

    @Table(name = "app_account") évite le conflit avec le mot réservé SQL "account".
*/
@Entity
@Table(name = "app_account")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq")
    @SequenceGenerator(name = "account_seq", sequenceName = "account_sequence", allocationSize = 1)
    private Long id;

    // Nom d'affichage de l'utilisateur (ex: "Jean Dupont")
    // Lombok ne génère PAS de getUsername() pour ce champ car getUsername() est déjà défini
    // manuellement plus bas (override de UserDetails). On expose ce champ via getDisplayUsername().
    @Column(nullable = false)
    private String username;

    // Identifiant unique de connexion — utilisé par Spring Security et comme subject du JWT
    @Column(unique = true, nullable = false)
    private String email;

    // Mot de passe haché en BCrypt — jamais stocké en clair
    @Column(nullable = false)
    private String password;

    // Rôle stocké en String (ROLE_USER ou ROLE_ADMIN) pour lisibilité en base
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // --- Implémentation de UserDetails ---

    /*
        Retourne l'EMAIL comme identifiant Spring Security.
        C'est cette valeur qui est stockée dans le SecurityContext et dans le subject du JWT.

        Note : Lombok ignore la génération de getUsername() pour le champ "username"
        car cette méthode est déjà définie ici.
    */
    @Override
    public String getUsername() {
        return email;
    }

    /*
        Exposé séparément pour accéder au nom d'affichage de l'utilisateur.
        Utilisé notamment pour inclure le username dans le payload du JWT.
    */
    public String getDisplayUsername() {
        return username;
    }

    /*
        getPassword() requis par UserDetails.
        Déclaré explicitement pour être clair, même si @Getter le génèrerait aussi.
    */
    @Override
    public String getPassword() {
        return password;
    }

    /*
        Retourne les autorisations de l'utilisateur.
        Spring Security utilise cette liste pour vérifier hasRole() et hasAuthority().
    */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    // Les méthodes suivantes permettent de gérer l'état du compte.
    // On retourne true par défaut — à personnaliser si besoin (ex: ban, expiration).
    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
