/**
 * 
 */
angular.module('menuView', [])
.factory('MenuRightService',
		[ '$resource', function($resource) {
			return $resource('/MenuRight/:id', {
				id : '@_id'
			});
		} ])

.controller('menuCtrl',
		[ 'MenuRightService', '$scope', '$http', function($menu, $scope, $http) {
			var self = this;
			self.menuRightList = $menu.query();
			self.displayRealtime = function(){
				return self.menuRightList.find(function(menu){
					return menu.id == 'RealtimeInfo'
				});
			}
			
			self.displayDataQuery = function(){
				return self.menuRightList.find(function(menu){
					return menu.id == 'DataQuery'
				});
			}
			
			self.displayReport = function(){
				return self.menuRightList.find(function(menu){
					return menu.id == 'Report'
				});
			}
			
			self.displayDataQuery = function(){
				return self.menuRightList.find(function(menu){
					return menu.id == 'DataQuery'
				});
			}
			
			self.displaySystemManagement = function(){
				return self.menuRightList.find(function(menu){
					return menu.id == 'SystemManagement'
				});
			}
		} ]);