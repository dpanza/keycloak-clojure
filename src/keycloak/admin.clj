(ns keycloak.admin
  (:require [clojure.tools.logging :as log :refer [info]]
            [clojure.java.data :refer [from-java]]
            [cheshire.core :as json :refer [encode]]
            [clojure.java.io :as io])
  (:import [org.keycloak.representations.idm CredentialRepresentation RealmRepresentation ClientRepresentation RoleRepresentation GroupRepresentation UserRepresentation]))

(defn realm-representation
  ([realm-name]
   (doto (RealmRepresentation.) (.setEnabled true) (.setRealm realm-name) (.setId realm-name)))
  ([realm-name theme]
   (doto (realm-representation realm-name)
     (.setAccountTheme theme)
     (.setEmailTheme theme)
     (.setLoginTheme theme))))

(defn create-realm!
  ([keycloak-client realm-name]
   (info "create realm" realm-name)
   (-> keycloak-client (.realms) (.create (realm-representation realm-name))))
  ([keycloak-client realm-name login-theme]
   (info "create realm" realm-name)
   (-> keycloak-client (.realms) (.create (realm-representation realm-name login-theme)))))

(defn delete-realm!
  [keycloak-client realm-name]
  (info "delete realm" realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.remove)))

(defn get-realm
  [keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.toRepresentation)))

(defn list-realms
  [keycloak-client]
  (info "list the realms")
  (-> keycloak-client (.realms) (.findAll)))

(defn role-representation "create a RoleRepresentation object" [name]
  (RoleRepresentation. name (str "Role created automatically by admin client") false))

(defn create-role!
  [keycloak-client realm-name role-name]
  (info "create role" role-name "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.roles) (.create (role-representation role-name))))


(defn group-representation "create a GroupRepresentation object" [group-name]
  (doto (GroupRepresentation.) (.setName group-name)))

(defn list-groups
  [keycloak-client realm-name]
  (info "list the groups representation objects of realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.groups)))

(defn get-group-id
  [keycloak-client realm-name group-name]
  (some #(if (= group-name (.getName %)) (.getId %)) (list-groups keycloak-client realm-name)))

(defn create-group!
  [keycloak-client realm-name group-name]
  (info "create group" group-name "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.add (group-representation group-name))))

(defn delete-group!
  [keycloak-client realm-name group-id]
  (info "delete group [id=" group-id "] in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.remove)))

(defn get-group
  [keycloak-client realm-name group-id]
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation)))

(defn get-subgroup
  [keycloak-client realm-name group-id subgroup-id]
  (some #(if (= subgroup-id (.getId %)) %)
        (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation) (.getSubGroups))))

(defn list-subgroups
  [keycloak-client realm-name group-id]
  (info "List all subgroups of group" group-id "in realm " realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation) (.getSubGroups)))

(defn get-subgroup-id
  [keycloak-client realm-name group-id subgroup-name]
  (some #(if (= subgroup-name (.getName %)) (.getId %)) (list-subgroups keycloak-client realm-name group-id)))

(defn create-subgroup!
  [keycloak-client realm-name group-id subgroup-name]
  (info "create subgroup" subgroup-name "in group" group-id "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.subGroup (group-representation subgroup-name))))


(defn user-representation
  [username]
  (doto (UserRepresentation.)
    (.setUsername username)))

(defn list-users
  [keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.users) (.list)))

(defn get-user-id
  [keycloak-client realm-name username]
  (info "get user id by username" username)
  (-> (some #(if (= username (.getUsername %)) (.getId %)) (list-users keycloak-client realm-name))))

(defn create-user!
  [keycloak-client realm-name username]
  (info "create user" username "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.create (user-representation username))))

(defn get-user
  [keycloak-client realm-name user-id]
  (info "get user [id=" user-id "] in realm " realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.toRepresentation)))

(defn add-user-to-group-by-username!
  [keycloak-client realm-name group-id username]
  (info "add user" username "in group" group-id "of realm" realm-name)
  (let [users-resources (-> keycloak-client (.realms) (.realm realm-name) (.users))
        user-id (-> users-resources (.search username) (first) (.getId))]
    (-> users-resources (.get user-id) (.joinGroup group-id))))

(defn add-user-to-group!
  [keycloak-client realm-name group-id user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.joinGroup group-id)))

(defn remove-user-from-group! [keycloak-client realm-name group-id user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.leaveGroup group-id)))

(defn delete-user!
  [keycloak-client realm-name user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.delete user-id)))

(defn get-user-groups
  [keycloak-client realm-name user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.groups)))

(defn client [client-name public?]
  (doto (ClientRepresentation.)
    (.setClientId client-name)
    (.setPublicClient public?)
    (.setStandardFlowEnabled true)
    (.setDirectAccessGrantsEnabled true)
    (.setServiceAccountsEnabled (not public?))
    (.setAuthorizationServicesEnabled (not public?))
    (.setRedirectUris ["http://localhost:3449/*"])
    (.setWebOrigins ["http://localhost:3449"])
    (.setName client-name)))

(defn create-client!
  [keycloak-client realm-name client-id public?]
  (info "create client" client-id "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.clients) (.create (client client-id public?))))

(defn get-client
  [keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first))

(defn get-client-secret
  [keycloak-client realm-name client-id]
  (let [id (-> (get-client keycloak-client realm-name client-id) (.getId))]
    (-> keycloak-client (.realm realm-name) (.clients) (.get id) (.getSecret) (.getValue))))
