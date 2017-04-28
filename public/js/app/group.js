/**
 * 
 */
angular
		.module(
				'groupView', ['ui.bootstrap'])
		.factory('MonitorTypeService', [ '$resource', function($resource) {
			return $resource('/MonitorType/:id', {
				id : '@_id'
			});
		} ])
		.factory('MonitorService', [ '$resource', function($resource) {
			return $resource('/Monitor/:id', {
				id : '@_id'
			});
		} ])
		.factory('IndParkService', [ '$resource', function($resource) {
			return $resource('/IndPark/:id', {
				id : '@_id'
			});
		} ])
		.factory('GroupService', [ '$resource', function($resource) {
			return $resource('/Group/:id', {
				id : '@_id'
			}, {
				update:  { method:'PUT' }
			});
		} ])
		.factory('MenuRightService', [ '$resource', function($resource) {
			return $resource('/MenuRight');
		} ])
		.controller(
				'groupCtrl',
				[
						'MonitorTypeService',
						'MonitorService',
						'IndParkService',
						'GroupService',
						'MenuRightService',
						'$scope',
						'$http',
						function($monitorType, $monitor, $indPark, $group, $menuRight, $scope, $http) {
							var self = this;
							self.monitorTypeList = $monitorType.query();
							self.monitorList = $monitor.query();
							self.indParkList = $indPark.query();
							self.menuRightList = $menuRight.query();
							self.groupList = $group.query();							
							self.indParkMonitor = function(m) {
								return self.selectedIndPark().indexOf(m.indParkName) != -1
							}
							
							function getSelectedArrayFunction(obj){
								return function(){
									var ret = [];
									for (var prop in obj) {
										if(obj[prop])
											ret.push(prop);
									}
									return ret;
								}	
							}
							
							self.groupName="";

							$scope.checkedMenuRight={};							
							self.selectedMenuRight = getSelectedArrayFunction($scope.checkedMenuRight);
							$scope.checkedIndPark={};
							self.selectedIndPark = getSelectedArrayFunction($scope.checkedIndPark);
							$scope.checkedMonitorId = {};
							$scope.checkedMtId = {};
							
							self.selectedGroupChanged = function(){
								var group = $scope.selectedGroup;
								$scope.checkedMenuRight={};
								self.menuRightList.forEach(function(menu){
									if(group.privilege.allowedMenuRights.indexOf(menu.id)!= -1)
										$scope.checkedMenuRight[menu.id]=true;
								})

								self.indParkList.forEach(function(park){
									if(group.privilege.allowedIndParks.indexOf(park)!= -1)
										$scope.checkedIndPark[park]=true;
								})
								
								self.monitorList.forEach(function(m){
									if(group.privilege.allowedMonitors.indexOf(m._id)!= -1){
										$scope.checkedMonitorId[m._id]=true;
									}										
								})
								
								self.monitorTypeList.forEach(function(mt){
									if(group.privilege.allowedMonitorTypes.indexOf(mt._id)!= -1){
										$scope.checkedMtId[mt._id]=true;
									}
								})
							}
							
							self.newGroup = function(){
								$group.save({_id:self.groupName}, function(resp){
									if(!resp.ok){
										alert("群組已經存在!");
									}else{
										self.groupList = $group.query();
										alert("群組建立成功!");
									}
								});
							}
							
							self.deleteGroup = function(){
								console.log($scope.selectedGroup);
								$group.remove({id:$scope.selectedGroup._id}, function(resp){
									if(!resp.ok){
										alert("群組不存在!");
									}else{
										self.groupList = $group.query();
										alert("群組已刪除!");
									}
								});
							} 
							
							self.updateGroup = function(){
								var updatedGroup = $scope.selectedGroup;
								updatedGroup.privilege.allowedMenuRights = 
									getSelectedArrayFunction($scope.checkedMenuRight)();
								updatedGroup.privilege.allowedIndParks =
									getSelectedArrayFunction($scope.checkedIndPark)();
								updatedGroup.privilege.allowedMonitors =
									getSelectedArrayFunction($scope.checkedMonitorId)();
								updatedGroup.privilege.allowedMonitorTypes =
									getSelectedArrayFunction($scope.checkedMtId)();
								
								console.log(updatedGroup)
								$group.update({id:$scope.selectedGroup._id}, updatedGroup,
										function(){alert("成功")}, function(){alert("失敗")});
							}
							self.query = function() {								
								var url = "/HistoryReport/" + encodeURIComponent($scope.selectedMonitorId) 
										+ "/" + encodeURIComponent($scope.selectedMtId().join(':'))
										+ "/hour"
										+ "/" + self.dateRangeStart.valueOf()
										+ "/" + self.dateRangeEnd.valueOf();

								$http
										.get(url)
										.then(
												function(result) {
													self.displayResult = true;
													$('#reportDiv').html(result.data);
												}, function(error) {
												});
							}														
						} ]);
