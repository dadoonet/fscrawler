# Test Duration Comparison (5 environments)

Run on 2026-06-03. Five environments compared:
- **Base**: Local Docker
- **AWS-EU (ECH)**: Elastic Cloud Hosted, AWS Ireland (eu-west-1)
- **AWS-EU (SL)**: Serverless, AWS Ireland
- **Azure-US (ECH)**: Elastic Cloud Hosted, Azure US-East
- **Azure-US (SL)**: Serverless, Azure US-East

## Key Observations

### Direct network latency impact
- **AWS-EU → Ireland**: ~85 ms RTT (GitHub Actions Virginia → AWS Ireland)
- **Azure-US → US-East**: ~1-3 ms RTT (GitHub Actions Azure Virginia → Azure US-East, same region/building)
- **Effect**: Azure-US tests are **10-20 s faster** than AWS-EU for tests > 15s

### Specific Issues

1. **`test_pause_resume_indexing`**: pathological everywhere, but less severe in Azure-US Serverless (40 s vs 128 s in AWS-EU Serverless)
   - AWS-EU SL: +2 min 08 s
   - Azure-US SL: +40 s
   - Indication: burst indexing issue + Serverless throttling, but regional improvement

2. **`subdirs_very_deep_tree`**: Azure-US Serverless is **2× faster** than AWS-EU Serverless (31 s vs 86 s)
   - Indicates that cumulative latency over a large number of ES requests has strong impact
   - Serverless Azure-US benefits greatly from network proximity

3. **Simple tests (< 10 s in Base)**: overhead ~8-10 s regardless of region (cold ES connection)
   - Base: 1-3 s
   - AWS-EU ECH: 8-10 s (+7-9 s latency)
   - Azure-US ECH: 3-5 s (+2-4 s latency)
   - **Azure-US gain: 30-60% latency reduction**

### Recommendation
For a drastic improvement in CI times:
1. Migrate ECH and Serverless to **AWS us-east-1** (Northern Virginia) for network latency close to 1-5 ms
2. Alternative: Azure-US already brings 30-50% improvement over AWS-EU

---

## Full Table (tests > 15s at least once)

| Class                             | Method                                                           | Base   | AWS-EU (ECH)   | AWS-EU (SL)   | Azure-US (ECH)   | Azure-US (SL)   |
|-----------------------------------|------------------------------------------------------------------|--------|----------------|---------------|------------------|-----------------|
| `FsCrawlerRestCrawlerControlIT`   | `test_pause_resume_indexing`                                     | 11 s   | 34 s           | 2 min 08 s    | 35 s             | 40 s            |
| `FsCrawlerTestSubDirsIT`          | `subdirs_very_deep_tree`                                         | 11 s   | 58 s           | 1 min 26 s    | 47 s             | 31 s            |
| `FsCrawlerTestAddNewFilesIT`      | `add_new_file`                                                   | 15 s   | 23 s           | 41 s          | 19 s             | 33 s            |
| `FsCrawlerTestMultipleCrawlersIT` | `multiple_crawlers`                                              | 4 s    | 18 s           | 34 s          | 10 s             | 22 s            |
| `FsCrawlerTestDatesIT`            | `check_dates`                                                    | 14 s   | 20 s           | 34 s          | 18 s             | 28 s            |
| `FsCrawlerTestRemoveDeletedIT`    | `move_file`                                                      | 13 s   | 21 s           | 33 s          | 17 s             | 26 s            |
| `FsCrawlerTestIncludesIT`         | `ignore_dir`                                                     | 15 s   | 23 s           | 29 s          | 18 s             | 23 s            |
| `FsCrawlerTestSshIT`              | `dir_with_space_at_the_end`                                      | 15 s   | 24 s           | 28 s          | 20 s             | 23 s            |
| `FsCrawlerRestCrawlerControlIT`   | `status_during_scan`                                             | 17 s   | 24 s           | 24 s          | 13 s             | 28 s            |
| `FsCrawlerTestAddNewFilesIT`      | `add_new_files_and_force_rescan`                                 | 9 s    | 18 s           | 25 s          | 13 s             | 18 s            |
| `FsCrawlerTestRemoveDeletedIT`    | `remove_deleted_enabled`                                         | 10 s   | 18 s           | 25 s          | 13 s             | 18 s            |
| `FsCrawlerTestAddNewFilesIT`      | `add_new_files_and_force_rescan_with_null`                       | 9 s    | 17 s           | 25 s          | 13 s             | 19 s            |
| `FsCrawlerTestFilenameAsIdIT`     | `remove_deleted_with_filename_as_id`                             | 9 s    | 17 s           | 25 s          | 13 s             | 18 s            |
| `FsCrawlerTestRemoveDeletedIT`    | `rename_file`                                                    | 10 s   | 16 s           | 25 s          | 13 s             | 18 s            |
| `FsCrawlerRestCrawlerControlIT`   | `test_delete_checkpoint_reindex`                                 | 5 s    | 11 s           | 25 s          | 8 s              | 19 s            |
| `FsCrawlerTestRemoveDeletedIT`    | `moving_files`                                                   | 10 s   | 16 s           | 25 s          | 13 s             | 19 s            |
| `FsCrawlerTestRemoveDeletedIT`    | `remove_folder_deleted_enabled`                                  | 11 s   | 20 s           | 23 s          | 17 s             | 18 s            |
| `FsCrawlerImplAllDocumentsIT`     | `fscrawler_2391_fs_crawler_impl_all_documents_i_t_all_documents` | —      | 13 s           | 22 s          | 8 s              | 11 s            |
| `FsCrawlerTestRawIT`              | `mapping`                                                        | 2 s    | 10 s           | 20 s          | 5 s              | 15 s            |
| `FsCrawlerTestSemanticIT`         | `semantic`                                                       | 20 s   | 10 s           | 18 s          | 6 s              | 11 s            |
| `FsCrawlerTestSshIT`              | `ssh`                                                            | 3 s    | 10 s           | 18 s          | 5 s              | 12 s            |
| `FsCrawlerTestStoreSourceIT`      | `store_source_no_index_content`                                  | 2 s    | 8 s            | 18 s          | 5 s              | 11 s            |
| `FsCrawlerTestFilenameAsIdIT`     | `filename_as_id`                                                 | 2 s    | 9 s            | 18 s          | 5 s              | 11 s            |
| `FsCrawlerTestExternalMetadataIT` | `metadata_static_and_external`                                   | 3 s    | 8 s            | 18 s          | 6 s              | 11 s            |
| `FsCrawlerTestFilesizeIT`         | `filesize_disabled`                                              | 2 s    | 9 s            | 17 s          | 3 s              | 11 s            |
| `FsCrawlerTestSubDirsIT`          | `subdirs_deep_tree`                                              | 2 s    | 14 s           | 17 s          | 7 s              | 11 s            |
| `FsCrawlerTestIncludesIT`         | `fscrawlerignore`                                                | 2 s    | 12 s           | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestDefaultsIT`         | `defaults`                                                       | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestIncludesIT`         | `subdirs_with_patterns`                                          | 2 s    | 12 s           | 17 s          | 7 s              | 11 s            |
| `FsCrawlerTestChecksumIT`         | `checksum_sha1`                                                  | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestMockParserIT`       | `mock_parser`                                                    | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestIngestPipelineIT`   | `ingest_pipeline`                                                | 2 s    | 9 s            | 17 s          | 4 s              | 11 s            |
| `FsCrawlerTestChecksumIT`         | `checksum_md5`                                                   | 2 s    | 7 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestExternalMetadataIT` | `metadata_static_basic_json`                                     | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestExternalMetadataIT` | `metadata_static_basic_yaml`                                     | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestRemoveDeletedIT`    | `remove_deleted_disabled`                                        | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestRawIT`              | `enable_raw`                                                     | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestAttributesIT`       | `acl_attributes`                                                 | 2 s    | 9 s            | 17 s          | 5 s              | 10 s            |
| `FsCrawlerTestStoreSourceIT`      | `do_not_store_source`                                            | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestSshIT`              | `ssh_with_key`                                                   | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestFilesizeIT`         | `indexed_chars_nolimit`                                          | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestJsonSupportIT`      | `add_as_inner_object`                                            | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestFilesizeIT`         | `indexed_chars`                                                  | 2 s    | 8 s            | 17 s          | 3 s              | 11 s            |
| `FsCrawlerTestSettingsIT`         | `bulk_flush`                                                     | 1 s    | 8 s            | 17 s          | 4 s              | 12 s            |
| `FsCrawlerTestOcrIT`              | `ocr_disabled`                                                   | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestJsonSupportIT`      | `json_disabled`                                                  | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestSubDirsIT`          | `subdirs`                                                        | 2 s    | 10 s           | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestFilesizeIT`         | `indexed_chars_percentage`                                       | 2 s    | 8 s            | 17 s          | 3 s              | 11 s            |
| `FsCrawlerTestJsonSupportIT`      | `json_support`                                                   | 2 s    | 9 s            | 17 s          | 5 s              | 10 s            |
| `FsCrawlerTestIndexContentIT`     | `index_content`                                                  | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestAttributesIT`       | `attributes`                                                     | 2 s    | 7 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestSettingsIT`         | `time_value`                                                     | 2 s    | 8 s            | 17 s          | 5 s              | 10 s            |
| `FsCrawlerTestExternalMetadataIT` | `metadata_external_json`                                         | 3 s    | 10 s           | 17 s          | 7 s              | 10 s            |
| `FsCrawlerTestDefaultsIT`         | `filename_analyzer`                                              | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestExternalMetadataIT` | `metadata_external_yaml`                                         | 3 s    | 11 s           | 17 s          | 7 s              | 11 s            |
| `FsCrawlerTestFilesizeIT`         | `filesize_limit`                                                 | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestStoreSourceIT`      | `store_source`                                                   | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestRawIT`              | `disable_raw`                                                    | 2 s    | 8 s            | 17 s          | 5 s              | 10 s            |
| `FsCrawlerTestExternalMetadataIT` | `metadata_static_empty`                                          | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestUnparsableIT`       | `unparsable`                                                     | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestXmlSupportIT`       | `xml_not_readable`                                               | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestZipFilesIT`         | `zip`                                                            | 2 s    | 8 s            | 17 s          | 4 s              | 11 s            |
| `FsCrawlerTestDefaultsIT`         | `highlight_documents`                                            | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestXmlSupportIT`       | `xml_support`                                                    | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestExternalMetadataIT` | `metadata_external_default`                                      | 2 s    | 10 s           | 17 s          | 7 s              | 11 s            |
| `FsCrawlerTestFiltersIT`          | `filter_visa_pattern_plus_foo`                                   | 2 s    | 8 s            | 17 s          | 8 s              | 11 s            |
| `FsCrawlerTestFilesizeIT`         | `filesize`                                                       | 2 s    | 9 s            | 17 s          | 3 s              | 11 s            |
| `FsCrawlerTestEmptyFilesIT`       | `empty_files`                                                    | 2 s    | 8 s            | 17 s          | 5 s              | 10 s            |
| `FsCrawlerTestFollowSymlinksIT`   | `follow_symlinks_enabled`                                        | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestFollowSymlinksIT`   | `follow_symlinks_disabled`                                       | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestUnparsableIT`       | `non_readable_file`                                              | 2 s    | 7 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestFiltersIT`          | `filter_visa_pattern`                                            | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestFiltersIT`          | `filter_one_term`                                                | 2 s    | 9 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestIngestPipelineIT`   | `ingest_pipeline_392`                                            | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestExternalMetadataIT` | `metadata_external_overwrite`                                    | 3 s    | 8 s            | 17 s          | 7 s              | 11 s            |
| `FsCrawlerTestXmlSupportIT`       | `xml_support_and_other_files`                                    | 2 s    | 8 s            | 17 s          | 5 s              | 11 s            |
| `FsCrawlerTestIncludesIT`         | `includes`                                                       | 2 s    | 8 s            | 16 s          | 3 s              | 10 s            |
| `FsCrawlerTestJsonSupportIT`      | `json_support_and_other_files`                                   | 2 s    | 8 s            | 16 s          | 5 s              | 11 s            |
| `FsCrawlerTestFTPIT`              | `ftp_with_user`                                                  | 3 s    | 9 s            | 16 s          | 7 s              | 12 s            |
| `FsCrawlerRestCrawlerControlIT`   | `test_pause_already_paused`                                      | 3 s    | 8 s            | 16 s          | 5 s              | 11 s            |
| `FsCrawlerTestDefaultsIT`         | `default_metadata`                                               | 2 s    | 8 s            | 16 s          | 5 s              | 10 s            |
| `FsCrawlerRestCrawlerControlIT`   | `test_delete_checkpoint_while_running`                           | 3 s    | 9 s            | 16 s          | 5 s              | 11 s            |
| `FsCrawlerTestLoopsIT`            | `single_loop`                                                    | 2 s    | 9 s            | 16 s          | 5 s              | 10 s            |
| `FsCrawlerRestCrawlerControlIT`   | `test_status_after_completed_scan`                               | 3 s    | 8 s            | 16 s          | 5 s              | 10 s            |
| `FsCrawlerTestFTPIT`              | `ftp`                                                            | 2 s    | 9 s            | 15 s          | 5 s              | 12 s            |
| `FsCrawlerRestFilenameAsIdIT`     | `upload_one_document`                                            | 2 s    | 9 s            | 15 s          | 4 s              | 10 s            |
| `FsCrawlerTestLoopsIT`            | `two_loops`                                                      | 9 s    | 15 s           | 15 s          | 12 s             | 10 s            |
