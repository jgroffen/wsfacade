/*
 * Copyright 2012 LISAsoft - lisasoft.com. 
 * All rights reserved.
 *
 * This file is part of Web Services Facade.
 *
 * Web Services Facade is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Web Services Facade is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Web Services Facade.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.lisasoft.wsfacade.wmts.models;

import com.lisasoft.wsfacade.models.AbstractModel;

public class GetTileResponseModel extends AbstractModel {

	public GetTileResponseModel(String modelPropertyNames) {
		for (String property : modelPropertyNames.split(",")) {
			String propertyName = property;
			String propertyValue = "";
			getProperties().put(propertyName.trim(), propertyValue.trim());
		}
	}
}
