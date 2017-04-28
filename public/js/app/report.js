/**
 * 
 */
angular
		.module(
				'reportView',
				[ 'ui.bootstrap.datetimepicker', 'ui.dateTimeInput',
						'smart-table' ])
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
		.controller(
				'reportCtrl',
				[
						'MonitorService',
						'IndParkService',
						'$scope',
						'$http',
						'$rootScope',
						function($monitor, $indPark, $scope, $http, $rootScope) {
							var self = this;
							self.monitorList = $monitor.query();
							self.indParkList = $indPark.query();
							self.indParkMonitor = function(m) {
								return (m.indParkName == $scope.selectedIndPark)
							}

							self.reportTypeList =[
							                      {id:'daily', name:'日報'},
							                      {id:'monthly', name:'月報'}							                      
							                      ];
							
							self.dateStart =  moment(0, "HH");
							
							$scope.selectedReportType;							
							$scope.selectedMonitorId;
							$scope.selectedIndPark="";
														
							self.displayResult = false;
							self.query = function() {
								var url="/monitorReport/"
									+ encodeURIComponent($scope.selectedMonitorId)
									+ "/" + $scope.selectedReportType
									+ "/" + self.dateStart.valueOf();

								$http
										.get(url)
										.then(
												function(result) {
													self.displayResult = true;
													$('#reportDiv').html(result.data);
										    		$("#downloadPDF").prop("href", "/PDF" + url);
													$("#downloadExcel").prop("href", "/Excel" + url);
												}, function(error) {
													alert(error)
												});
							}														
						} ]);