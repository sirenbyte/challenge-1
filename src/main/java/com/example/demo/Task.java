package com.example.demo;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.schemas.JavaFieldSchema;
import org.apache.beam.sdk.schemas.annotations.DefaultSchema;
import org.apache.beam.sdk.schemas.annotations.SchemaCreate;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.beam.sdk.values.TypeDescriptors.*;

public class Task {
    public static void main(String[] args) {
        PipelineOptions options = PipelineOptionsFactory.fromArgs(args).withValidation().create();
        options.setTempLocation("input.csv");
        runChallenge(options);
    }

    static void runChallenge(PipelineOptions options) {
        Pipeline p = Pipeline.create(options);

        // 1) Parse the CSV file and convert each row into a dictionary.
        PCollection<Review> reviewPCollection = p.apply("ReadLines", TextIO.read().from(options.getTempLocation()))
                .apply("Result", ParDo.of(new ExtractReviewFn()));


        // 2) Calculate the average rating for each movie.
        reviewPCollection
                .apply(MapElements.into(kvs(integers(), integers()))
                        .via(review -> KV.of(review.movieId, review.rating)))
                .apply(Mean.perKey())
                .apply(MapElements.into(strings()).via(String::valueOf))
                .apply("Average rating for each movie", TextIO.write().to("2-challenge_result"));

        // 3) Count the total number of reviews for each movie.
        reviewPCollection.apply(MapElements.into(kvs(integers(), integers()))
                        .via(review -> KV.of(review.movieId, review.reviewId)))
                .apply(Count.perKey())
                .apply(MapElements.into(strings()).via(String::valueOf))
                .apply("Total review for each movie", TextIO.write().to("3-challenge_result"));

        // 4) Find the top 10 movies with the highest average rating and at least 2 reviews.
        reviewPCollection
                .apply(MapElements.into(kvs(integers(), integers())).via(review -> KV.of(review.movieId, review.rating)))
                .apply(GroupByKey.create())
                .apply(Filter.by(review -> {
                    List<Integer> myList = new ArrayList<>();
                    review.getValue().forEach(myList::add);
                    return myList.size() > 2;
                }))
                .apply(ParDo.of(new AverageFn()))
                .apply(MapElements.into(strings()).via(String::valueOf))
                .apply("Highest average rating and least 2 reviews", TextIO.write().to("4-challenge_result"));


        // 6) Calculate the average rating given by each user.
        PCollection<KV<Integer, Double>> averageRating = reviewPCollection
                .apply(MapElements.into(kvs(integers(), integers())).via(review -> KV.of(review.userId, review.rating)))
                .apply(Mean.perKey());

        // averageRating result
        averageRating
                .apply(MapElements.into(strings()).via(String::valueOf))
                .apply("Average rating by user", TextIO.write().to("6-challenge_result"));


        // 7) Find the top 5 most generous users (users with the highest average ratings).
        averageRating
                .apply(Top.of(5, (a, b) -> a.getValue().compareTo(b.getValue())))
                .apply(MapElements.into(strings()).via(reviewList -> reviewList.stream().map(review -> review.getKey() + " " + review.getValue()).collect(Collectors.joining("\n"))))
                .apply("Top 5 highest average ratings", TextIO.write().to("7-challenge_result"));


        // 8) Count the number of words in each review.
        reviewPCollection
                .apply(MapElements.into(kvs(integers(), integers())).via(review -> KV.of(review.reviewId, review.reviewText.split(" ").length)))
                .apply(MapElements.into(strings()).via(String::valueOf))
                .apply("Average word each review", TextIO.write().to("8-challenge_result"));


        // 9) Calculate the average word count for reviews of each movie.
        PCollection<KV<Integer, Double>> averageWordEachReview = reviewPCollection
                .apply(MapElements.into(kvs(integers(), integers())).via(review -> KV.of(review.movieId, review.reviewText.split(" ").length)))
                .apply(Mean.perKey());

        // averageWordEachReview result
        averageWordEachReview
                .apply(MapElements.into(strings()).via(String::valueOf))
                .apply("Average word each review", TextIO.write().to("9-challenge_result"));


        // 10) Identify the top 5 movies with the longest reviews on average.
        averageWordEachReview
                .apply(Top.of(5, (a, b) -> a.getValue().compareTo(b.getValue())))
                .apply(MapElements.into(strings()).via(reviewList -> reviewList.stream().map(review -> review.getKey() + " " + review.getValue()).collect(Collectors.joining("\n"))))
                .apply("Top 5 longest review", TextIO.write().to("10-challenge_result"));


        // 11) Determine the distribution of ratings
        reviewPCollection
                .apply(MapElements.into(kvs(integers(), integers())).via(review -> KV.of(review.rating, review.movieId)))
                .apply(GroupByKey.create())
                .apply(MapElements.into(strings()).via(String::valueOf))
                .apply("Rating", TextIO.write().to("11-challenge_result"));


        p.run();
    }

    static class ExtractReviewFn extends DoFn<String, Review> {
        @ProcessElement
        public void processElement(ProcessContext c) throws IOException {
            String[] items = c.element().split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            try {
                c.output(new Review(Integer.valueOf(items[0]), items[1], Integer.valueOf(items[2]), Integer.valueOf(items[3]), Integer.valueOf(items[4])));
            } catch (Exception e) {
                System.out.println("Skip header");
            }
        }
    }

    static class AverageFn extends DoFn<KV<Integer, Iterable<Integer>>, KV<Integer, Double>> {
        @ProcessElement
        public void processElement(ProcessContext c) {
            double sum = 0;
            int count = 0;
            for (int i : c.element().getValue()) {
                sum += i;
                count += 1;
            }
            c.output(KV.of(c.element().getKey(), sum / count));
        }
    }

    @DefaultSchema(JavaFieldSchema.class)
    public static class Review {
        public Integer reviewId;
        public Integer movieId;
        public Integer userId;
        public Integer rating;
        public String reviewText;

        @Override
        public String toString() {
            return "Review{" +
                    "reviewId=" + reviewId +
                    ", movieId=" + movieId +
                    ", userId=" + userId +
                    ", rating=" + rating +
                    ", reviewText='" + reviewText + '\'' +
                    '}';
        }

        public Review() {

        }

        @SchemaCreate
        public Review(Integer reviewId, String reviewText, Integer rating, Integer userId, Integer movieId) {
            this.reviewId = reviewId;
            this.movieId = movieId;
            this.userId = userId;
            this.rating = rating;
            this.reviewText = reviewText;
        }
    }
}