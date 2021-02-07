/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
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
package com.dremio.exec.record.selection;

import org.apache.arrow.memory.ArrowBuf;

import com.dremio.exec.record.DeadBuf;
import com.google.common.base.Preconditions;

public class SelectionVector4 implements AutoCloseable {
  // private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SelectionVector4.class);

  private ArrowBuf data;
  private int recordCount;
  private int start;
  private int length;

  public SelectionVector4(ArrowBuf vector, int recordCount, int batchRecordCount) {
    Preconditions.checkArgument(recordCount < Integer.MAX_VALUE / 4,
        "Currently, Dremio can only support allocations up to 2gb in size.  "
        + "You requested an allocation of %s bytes.", recordCount * 4);
    this.recordCount = recordCount;
    this.start = 0;
    this.length = Math.min(batchRecordCount, recordCount);
    this.data = vector;
  }

  public int getTotalCount() {
    return recordCount;
  }

  public int getCount() {
    return length;
  }

  /**
   * Get location of current start point.
   * @return Memory address where current batch starts.
   */
  public long getMemoryAddress() {
    return data.memoryAddress() + start * 4;
  }

  public void set(int index, int compound) {
    data.setInt(index*4, compound);
  }

  public void set(int index, int recordBatch, int recordIndex) {
    data.setInt(index*4, (recordBatch << 16) | (recordIndex & 65535));
  }

  public int get(int index) {
    return data.getInt( (start+index)*4);
  }

  /**
   * Caution: This method shares the underlying buffer between this vector and the newly created one.
   * @param batchRecordCount this will be used when creating the new vector
   * @return Newly created single batch SelectionVector4.
   */
  public SelectionVector4 createNewWrapperCurrent(int batchRecordCount) {
    data.retain();
    final SelectionVector4 sv4 = new SelectionVector4(data, recordCount, batchRecordCount);
    sv4.start = this.start;
    return sv4;
  }

  /**
   * Caution: This method shares the underlying buffer between this vector and the newly created one.
   * @return Newly created single batch SelectionVector4.
   */
  public SelectionVector4 createNewWrapperCurrent() {
    return createNewWrapperCurrent(length);
  }

  public boolean next() {
//    logger.debug("Next called. Start: {}, Length: {}, recordCount: " + recordCount, start, length);

    if (start + length >= recordCount) {

      start = recordCount;
      length = 0;
//      logger.debug("Setting count to zero.");
      return false;
    }

    start = start+length;
    int newEnd = Math.min(start+length, recordCount);
    length = newEnd - start;
//    logger.debug("New start {}, new length {}", start, length);
    return true;
  }

  public void clear() {
    start = 0;
    length = 0;
    if (data != DeadBuf.DEAD_BUFFER) {
      data.release();
      data = DeadBuf.DEAD_BUFFER;
    }
  }

  @Override
  public void close() {
    clear();
  }
}
