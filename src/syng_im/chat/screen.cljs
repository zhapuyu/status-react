(ns syng-im.chat.screen
  (:require-macros [syng-im.utils.views :refer [defview]])
  (:require [clojure.string :as s]
            [re-frame.core :refer [subscribe dispatch]]
            [syng-im.components.react :refer [view
                                              text
                                              image
                                              icon
                                              touchable-highlight
                                              list-view
                                              list-item]]
            [syng-im.chat.styles.screen :as st]
            [syng-im.resources :as res]
            [syng-im.utils.listview :refer [to-datasource]]
            [syng-im.components.invertible-scroll-view :refer [invertible-scroll-view]]
            [syng-im.components.toolbar :refer [toolbar]]
            [syng-im.chat.views.message :refer [chat-message]]
            [syng-im.chat.views.new-message :refer [chat-message-new]]))


(defn contacts-by-identity [contacts]
  (->> contacts
       (map (fn [{:keys [identity] :as contact}]
              [identity contact]))
       (into {})))

(defn add-msg-color [{:keys [from] :as msg} contact-by-identity]
  (if (= "system" from)
    (assoc msg :text-color :#4A5258
               :background-color :#D3EEEF)
    (let [{:keys [text-color background-color]} (get contact-by-identity from)]
      (assoc msg :text-color text-color
                 :background-color background-color))))

(defn chat-photo [{:keys [photo-path]}]
  [view {:margin       10
         :borderRadius 50}
   [image {:source (if (s/blank? photo-path)
                     res/user-no-photo
                     {:uri photo-path})
           :style  st/chat-photo}]])

(defn contact-online [{:keys [online]}]
  (when online
    [view st/online-view
     [view st/online-dot-left]
     [view st/online-dot-right]]))

(defn typing [member]
  [view st/typing-view
   [view st/typing-background
    [text {:style st/typing-text}
     (str member " is typing")]]])

(defn typing-all []
  [view st/typing-all
   (for [member ["Geoff" "Justas"]]
     ^{:key member} [typing member])])

(defn message-row [contact-by-identity group-chat]
  (fn [row _ idx]
    (let [msg (-> row
                  (add-msg-color contact-by-identity)
                  (assoc :group-chat group-chat)
                  (assoc :last-msg (zero? (js/parseInt idx))))]
      (list-item [chat-message msg]))))

(defn on-action-selected [position]
  (case position
    0 (dispatch [:show-add-participants])
    1 (dispatch [:show-remove-participants])
    2 (dispatch [:leave-group-chat])))

(defn overlay [{:keys [on-click-outside]} items]
  [view st/actions-overlay
   [touchable-highlight {:on-press on-click-outside
                         :style    st/overlay-highlight}
    [view nil]]
   items])

(defn action-view [{:keys     [icon-style custom-icon handler title subtitle]
                    icon-name :icon}]
  [touchable-highlight {:on-press (fn []
                                    (dispatch [:set-show-actions false])
                                    (when handler
                                      (handler)))}
   [view st/action-icon-row
    [view st/action-icon-view
     (or custom-icon
         [icon icon-name icon-style])]
    [view st/action-view
     [text {:style st/action-title} title]
     (when-let [subtitle subtitle]
       [text {:style st/action-subtitle}
        subtitle])]]])

(defn menu-item-contact-photo [{:keys [photo-path]}]
  [image {:source (if (s/blank? photo-path)
                    res/user-no-photo
                    {:uri photo-path})
          :style  st/menu-item-profile-contact-photo}])

(defn menu-item-contact-online [{:keys [online]}]
  (when online
    [view st/menu-item-profile-online-view
     [view st/menu-item-profile-online-dot-left]
     [view st/menu-item-profile-online-dot-right]]))

(defn menu-item-icon-profile []
  [view st/icon-view
   [menu-item-contact-photo {}]
   [menu-item-contact-online {:online true}]])

(defn actions-list-view []
  (let [{:keys [group-chat chat-id]}
        (subscribe [:chat-properties [:group-chat :chat-id]])]
    (when-let [actions (if @group-chat
                         [{:title      "Add Contact to chat"
                           :icon       :menu_group
                           :icon-style {:width  25
                                        :height 19}
                           :handler    #(dispatch [:show-add-participants])}
                          {:title      "Remove Contact from chat"
                           :subtitle   "Alex, John"
                           :icon       :search_gray_copy
                           :icon-style {:width  17
                                        :height 17}
                           :handler    #(dispatch [:show-remove-participants])}
                          {:title      "Leave Chat"
                           :icon       :muted
                           :icon-style {:width  18
                                        :height 21}
                           :handler    #(dispatch [:leave-group-chat])}
                          {:title      "Settings"
                           :icon       :settings
                           :icon-style {:width  20
                                        :height 13}
                           :handler    #(dispatch [:show-group-settings])}]
                         [{:title      "Profile"
                           :custom-icon [menu-item-icon-profile]
                           :icon       :menu_group
                           :icon-style {:width  25
                                        :height 19}
                           :handler    #(dispatch [:show-profile @chat-id])}
                          {:title      "Search chat"
                           :subtitle   "!not implemented"
                           :icon       :search_gray_copy
                           :icon-style {:width  17
                                        :height 17}
                           :handler    nil}
                          {:title      "Notifications and sounds"
                           :subtitle   "!not implemented"
                           :icon       :muted
                           :icon-style {:width  18
                                        :height 21}
                           :handler    nil}
                          {:title      "Settings"
                           :subtitle   "!not implemented"
                           :icon       :settings
                           :icon-style {:width  20
                                        :height 13}
                           :handler    (fn [])}])]
      [view st/actions-wrapper
       [view st/actions-separator]
       [view st/actions-view
        (for [action actions]
          ^{:key action} [action-view action])]])))

(defn actions-view []
  [overlay {:on-click-outside #(dispatch [:set-show-actions false])}
   [actions-list-view]])

(defn toolbar-content []
  (let [{:keys [group-chat name contacts]}
        (subscribe [:chat-properties [:group-chat :name :contacts]])
        show-actions (subscribe [:show-actions])]
    (fn []
      [view (st/chat-name-view @show-actions)
       [text {:style st/chat-name-text}
        (or @name "Chat name")]
       (if @group-chat
         [view {:flexDirection :row}
          [icon :group st/group-icon]
          [text {:style st/members}
           (let [cnt (count @contacts)]
             (str cnt
                  (if (< 1 cnt)
                    " members"
                    " member")
                  ", " cnt " active"))]]
         [text {:style st/last-activity} "Active a minute ago"])])))

(defn toolbar-action []
  (let [show-actions (subscribe [:show-actions])]
    (fn []
      (if @show-actions
        [touchable-highlight
         {:on-press #(dispatch [:set-show-actions false])}
         [view st/icon-view
          [icon :up st/up-icon]]]
        [touchable-highlight
         {:on-press #(dispatch [:set-show-actions true])}
         [view st/icon-view
          [chat-photo {}]
          [contact-online {:online true}]]]))))

(defn chat-toolbar []
  (let [{:keys [group-chat name contacts]}
        (subscribe [:chat-properties [:group-chat :name :contacts]])
        show-actions (subscribe [:show-actions])]
    (fn []
      [toolbar {:hide-nav?      @show-actions
                :custom-content [toolbar-content]
                :custom-action  [toolbar-action]}])))

(defview messages-view [group-chat]
  [messages [:chat :messages]
   contacts [:chat :contacts]]
  (let [contacts' (contacts-by-identity contacts)]
    [list-view {:renderRow             (message-row contacts' group-chat)
                :renderScrollComponent #(invertible-scroll-view (js->clj %))
                :onEndReached          #(dispatch [:load-more-messages])
                :enableEmptySections   true
                :dataSource            (to-datasource messages)}]))

(defview chat []
  [group-chat [:chat :group-chat]
   show-actions-atom [:show-actions]]
  [view st/chat-view
   [chat-toolbar]
   [messages-view group-chat]
   (when group-chat [typing-all])
   [chat-message-new]
   (when show-actions-atom [actions-view])])