/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// angular.module('zeppelinWebApp').controller('NavCtrl', NavCtrl)

import navBarTemplate from './navbar.html'

class NavBarController {
  constructor($scope, $rootScope, $http, $routeParams, $location,
              noteListFactory, baseUrlSrv, websocketMsgSrv,
              arrayOrderingSrv, searchService, TRASH_FOLDER_ID) {
    'ngInject'

    this.$scope = $scope
    this.$http = $http
    this.$rootScope = $rootScope
    this.$routeParams = $routeParams
    this.$location = $location
    this.noteListFactory = noteListFactory
    this.baseUrlSrv = baseUrlSrv
    this.websocketMsgSrv = websocketMsgSrv
    this.arrayOrderingSrv = arrayOrderingSrv
    this.connected = websocketMsgSrv.isConnected()
    // this.isActive = this.isActive()
    // this.logout = logout()
    this.notes = noteListFactory
    // this.search = search
    this.searchForm = searchService
    // this.showLoginWindow = showLoginWindow
    this.TRASH_FOLDER_ID = TRASH_FOLDER_ID
    // this.isFilterNote = isFilterNote

    this.$scope.query = {q: ''}

    this.$scope.isDrawNavbarNoteList = false
    angular.element('#notebook-list').perfectScrollbar({suppressScrollX: true})

    angular.element(document).click(function () {
      this.$scope.query.q = ''
    })

    this.getZeppelinVersion()
    this.loadNotes()

    /**
     * $scope.$on functions below
     */

    this.$scope.$on('setNoteMenu', function (event, notes) {
      noteListFactory.setNotes(notes)
      this.initNotebookListEventListener()
    })

    this.$scope.$on('setConnectedStatus', function (event, param) {
      this.connected = param
    })

    this.$scope.$on('loginSuccess', function (event, param) {
      $rootScope.ticket.screenUsername = $rootScope.ticket.principal
      this.listConfigurations()
      this.loadNotes()
      this.getHomeNote()
    })
    this.$scope.calculateTooltipPlacement = function (note) {
      if (note !== undefined && note.name !== undefined) {
        let length = note.name.length
        if (length < 2) {
          return 'top-left'
        } else if (length > 7) {
          return 'top-right'
        }
      }
      return 'top'
    }
  }

  getZeppelinVersion() {
    this.$http.get(this.baseUrlSrv.getRestApiBase() + '/version').success(
      function (data, status, headers, config) {
        this.$rootScope.zeppelinVersion = data.body.version
      }).error(
      function (data, status, headers, config) {
        console.log('Error %o %o', status, data.message)
      })
  }

  isFilterNote(note) {
    if (!this.$scope.query.q) {
      return true
    }

    let noteName = note.name
    if (noteName.toLowerCase().indexOf(this.$scope.query.q.toLowerCase()) > -1) {
      return true
    }
    return false
  }

  isActive(noteId) {
    return (this.$routeParams.noteId === noteId)
  }

  listConfigurations() {
    this.websocketMsgSrv.listConfigurations()
  }

  loadNotes() {
    this.websocketMsgSrv.getNoteList()
  }

  getHomeNote() {
    this.websocketMsgSrv.getHomeNote()
  }

  logout() {
    let logoutURL = this.baseUrlSrv.getRestApiBase() + '/login/logout'

    // for firefox and safari
    logoutURL = logoutURL.replace('//', '//false:false@')
    this.$http.post(logoutURL).error(function () {
      // force authcBasic (if configured) to logout
      this.$http.post(logoutURL).error(function () {
        this.$rootScope.userName = ''
        this.$rootScope.ticket.principal = ''
        this.$rootScope.ticket.screenUsername = ''
        this.$rootScope.ticket.ticket = ''
        this.$rootScope.ticket.roles = ''
        BootstrapDialog.show({
          message: 'Logout Success'
        })
        setTimeout(function () {
          window.location = this.baseUrlSrv.getBase()
        }, 1000)
      })
    })
  }

  search(searchTerm) {
    this.$location.path('/search/' + searchTerm)
  }

  showLoginWindow() {
    setTimeout(function () {
      angular.element('#userName').focus()
    }, 500)
  }

  /*
   ** Performance optimization for Browser Render.
   */
  initNotebookListEventListener() {
    angular.element(document).ready(function () {
      angular.element('.notebook-list-dropdown').on('show.bs.dropdown', function () {
        this.$scope.isDrawNavbarNoteList = true
      })

      angular.element('.notebook-list-dropdown').on('hide.bs.dropdown', function () {
        this.$scope.isDrawNavbarNoteList = false
      })
    })
  }
}

export const NavBarComponent = {
  template: navBarTemplate,
  controller: NavBarController,
}

export const NavBarModule = angular
  .module('zeppelinWebApp')
  .component('nav', NavBarComponent)
  .name
