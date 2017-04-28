/**
 * 
 */
angular
		.module('userView', [ 'ui.bootstrap' ])
		.factory('MonitorTypeService', [ '$resource', function($resource) {
			return $resource('/MonitorType/:id', {
				id : '@_id'
			});
		} ])
		.factory('GroupService', [ '$resource', function($resource) {
			return $resource('/Group/:id', {
				id : '@_id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ])
		.factory('UserService', [ '$resource', function($resource) {
			return $resource('/User/:id', {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ])
		.controller(
				'userCtrl',
				[
						'MonitorTypeService',
						'GroupService',
						'UserService',
						'$scope',
						'$http',
						function($monitorType, $group, $user, $scope, $http) {
							var self = this;
							self.monitorTypeList = $monitorType.query();
							self.groupList = $group.query();
							self.userList = $user.query();
							self.user = {};
							self.user.isAdmin = false;
							self.user.email = "";
							self.password = "";
							self.user.name = "";
							self.user.phone = "";

							function getSelectedArrayFunction(obj) {
								return function() {
									var ret = [];
									for ( var prop in obj) {
										if (obj[prop])
											ret.push(prop);
									}
									return ret;
								}
							}

							$scope.checkedMtId = {};

							self.selectedUserChanged = function() {
								var user = $scope.selectedUser;

								self.monitorTypeList.forEach(function(mt) {
									if (user.widgets.indexOf(mt._id) != -1) {
										$scope.checkedMtId[mt._id] = true;
									}
								})
							}

							self.userReady = function() {
								return self.user.name
										&& self.user.password
										&& self.user.passwordRetype
										&& self.user.password === self.user.passwordRetype
										&& self.user.email
										&& self.user.password
										&& $scope.userGroup
							}

							self.newUser = function() {
								self.user.groupId = $scope.userGroup._id;
								$user.save({}, self.user, function(resp) {
									if (!resp.ok) {
										alert("使用者已經存在!");
									} else {
										self.userList = $user.query();
										alert("使用者建立成功!");
									}
									self.userList = $user.query();
								}, function() {
									alert("發生錯誤!")
								});
							}

							self.deleteUser = function() {
								$user.remove({
									id : $scope.selectedUser.email
								}, function(resp) {
									if (!resp.ok) {
										alert("使用者不存在!");
									} else {
										self.groupList = $group.query();
										alert("使用者已刪除!");
									}
									self.userList = $user.query();
								}, function() {
									alert("發生錯誤!")
								});
							}

							self.displayUser = false;
							self.copyUser = function() {
								if ($scope.selectedUser) {
									self.user = $scope.selectedUser;
									$scope.userGroup = self.groupList
											.find(function(g) {
												return g._id == self.user.groupId;
											});
									self.user.passwordRetype = self.user.password;

									$scope.widgets = {};
									self.monitorTypeList
											.forEach(function(mt) {
												if (self.user.widgets
														.indexOf(mt._id) != -1)
													$scope.widgets[mt._id] = true;
											});

									self.displayUser = true;
								}
							}

							self.updateUser = function() {
								var user = self.user;
								user.groupId = $scope.userGroup._id;
								user.widgets = getSelectedArrayFunction(
										$scope.widgets)();
								$user.update({
									id : user.email
								}, user, function() {
									alert("成功")
								}, function() {
									alert("失敗")
								});
							}

							self.testSMS = function() {
								$http({
									method : 'GET',
									url : '/TestSMS/' + self.user.phone
								}).then(function successCallback(response) {
									alert("送出簡訊");
								}, function errorCallback(response) {
									alert("發生錯誤");
								});
							}
						} ]);
