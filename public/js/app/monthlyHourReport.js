/**
 * 
 */
angular
		.module(
				'monthlyHourReportView',
				[ 'ui.bootstrap.datetimepicker', 'ui.dateTimeInput',
						'smart-table' ])
		.factory('MonitorService', [ '$resource', function($resource) {
			return $resource('/Monitor/:id', {
				id : '@_id'
			});
		} ])
		.factory('MonitorTypeService', [ '$resource', function($resource) {
			return $resource('/MonitorType/:id', {
				id : '@_id'
			});
		} ])
		.factory('IndParkService', [ '$resource', function($resource) {
			return $resource('/IndPark/:id', {
				id : '@_id'
			});
		} ])
		.controller(
				'monthlyHourReportCtrl',
				[
						'MonitorService',
						'IndParkService',
						'MonitorTypeService',
						'$scope',
						'$http',
						'$rootScope',
						function($monitor, $indPark, $monitorType, $scope, $http, $rootScope) {
							var self = this;
							self.monitorList = $monitor.query();
							self.monitorTypeList = $monitorType.query();
							self.indParkList = $indPark.query();
							self.indParkMonitor = function(m) {
								return (m.indParkName == $scope.selectedIndPark)
							}
							
							self.dateStart =  moment(0, "HH");
														
							self.displayResult = false;
							self.query = function() {
								var url="/MonthlyHourReport/"
									+ encodeURIComponent($scope.selectedMonitorId)
									+ "/" + encodeURIComponent($scope.selectedMtId)
									+ "/" + self.dateStart.valueOf();

								$http
										.get(url)
										.then(
												function(result) {
													self.displayResult = true;
													$('#reportDiv').html(result.data);
										    		$("#downloadPDF").prop("href", "/PDF" + url);
													//$("#downloadExcel").prop("href", "/Excel" + url);
												}, function(error) {
												});
							}														
						} ]);